package controllers.institutions

import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionException

import actors.harvesting.OaiPmhHarvesterManager.{OaiPmhHarvestData, OaiPmhHarvestJob}
import actors.harvesting.{OaiPmhHarvester, OaiPmhHarvesterManager}
import actors.ingest
import actors.transformation.XmlConverterManager.{XmlConvertData, XmlConvertJob}
import actors.transformation.{XmlConverter, XmlConverterManager}
import akka.actor.Props
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.FileStage
import javax.inject._
import models.HarvestEvent.HarvestEventType
import models._
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{Format, Json, Writes}
import play.api.libs.streams.Accumulator
import play.api.mvc._
import services.data.{ApiUser, DataHelpers}
import services.datasets.{ImportDatasetExists, ImportDatasetService}
import services.harvesting.{HarvestEventService, OaiPmhClient, OaiPmhConfigService, OaiPmhError}
import services.ingest.IngestApi.{IngestData, IngestDataType, IngestJob}
import services.ingest._
import services.search._
import services.storage.{FileMeta, FileStorage}
import services.transformation._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration._

case class FileToUpload(name: String, `type`: String, size: Long)

object FileToUpload {
  implicit val _json: Format[FileToUpload] = Json.format[FileToUpload]
}

case class IngestPayload(
  logMessage: String,
  tolerant: Boolean = false,
  commit: Boolean = false,
  properties: Option[String] = None,
  files: Seq[String] = Seq.empty
)

object IngestPayload {
  implicit val _json: Format[IngestPayload] = Json.format[IngestPayload]
}

case class ValidationResult(key: String, eTag: Option[String], errors: Seq[XmlValidationError])

object ValidationResult {
  implicit val _json: Writes[ValidationResult] = Json.writes[ValidationResult]
}


@Singleton
case class RepositoryData @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  @Named("dam") storage: FileStorage,
  eadValidator: EadValidator,
  ingestApi: IngestApi,
  importLogService: ImportLogService,
  oaipmhClient: OaiPmhClient,
  oaipmhConfigs: OaiPmhConfigService,
  harvestEvents: HarvestEventService,
  xmlTransformer: XmlTransformer,
  dataTransformations: DataTransformationService,
  datasets: ImportDatasetService,
  asyncCache: AsyncCacheApi,
  @NamedCache("transformer-cache") transformCache: AsyncCacheApi
)(
  implicit mat: Materializer
) extends AdminController
  with Read[Repository]
  with Update[Repository] {
  private val logger = play.api.Logger(classOf[RepositoryData])

  private val repositoryDataRoutes = controllers.institutions.routes.RepositoryData

  private val fileForm = Form(single("file" -> text))
  private val bucket = config.get[String]("storage.dam.classifier")

  private def instance(implicit request: RequestHeader): String =
    URLEncoder.encode(config.getOptional[String]("storage.instance").getOrElse(request.host), "UTF-8")

  private def prefix(id: String, ds: String, stage: FileStage.Value)(implicit request: RequestHeader): String = s"$instance/$id/$ds/$stage/"

  def manager(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.isVersioned(bucket).map { versioned =>
      Ok(views.html.admin.repository.datamanager(request.item, versioned))
    }
  }

  def listFiles(id: String, ds: String, stage: FileStage.Value, path: Option[String], from: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket,
      prefix = Some(prefix(id, ds, stage) + path.getOrElse("")),
      from.map(key => s"${prefix(id, ds, stage)}$key"), max = 500).map { list =>
      Ok(Json.toJson(list.copy(files = list.files.map(f => f.copy(key = f.key.replace(prefix(id, ds, stage), ""))))))
    }
  }

  def countFiles(id: String, ds: String, stage: FileStage.Value, path: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._

    val pathPrefix: String = prefix(id, ds, stage) + path.getOrElse("")
    asyncCache.getOrElseUpdate(s"bucket:count:$bucket/$pathPrefix", 1.minute) {
      storage.count(bucket, Some(prefix(id, ds, stage) + path.getOrElse(""))).map { count =>
        Json.obj("path" -> pathPrefix, "count" -> count)
      }
    }.map(Ok(_))
  }

  def fileUrls(id: String, ds: String, stage: FileStage.Value): Action[Seq[String]] = EditAction(id).apply(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id, ds, stage)}$path")
    val result = keys.map(key => key.replace(prefix(id, ds, stage), "") -> storage.uri(bucket, key)).toMap
    Ok(Json.toJson(result))
  }

  def validateFiles(id: String, ds: String, stage: FileStage.Value): Action[Map[String, String]] = Action.async(parse.json[Map[String, String]]) { implicit request =>
    val pre = prefix(id, ds, stage)
    val results = request.body.toSeq.map { case (tag, key) =>
      val url = storage.uri(bucket, pre + key).toString
      eadValidator.validateEad(Uri(url))
        .map(errs => ValidationResult(key, Some(tag), errs))
    }
    Future.sequence(results).map(out => Ok(Json.toJson(out))).recover {
      case e => BadRequest(Json.obj("error" -> e.getMessage))
    }
  }

  def validateAll(id: String, ds: String, stage: FileStage.Value): Action[AnyContent] = EditAction(id).async { implicit request =>
    val pre = prefix(id, ds, stage)
    val results: Future[Seq[ValidationResult]] = storage
      .streamFiles(bucket, Some(pre))
      .mapAsync(3) { f =>
        eadValidator.validateEad(Uri(storage.uri(bucket, f.key).toString))
          .map(errs =>
            ValidationResult(f.key.replace(prefix(id, ds, stage), ""), f.eTag, errs))
      }
      .runWith(Sink.seq)

    results.map(out => Ok(Json.toJson(out))).recover {
      case e => BadRequest(Json.obj("error" -> e.getMessage))
    }
  }

  def ingestAll(id: String, ds: String, stage: FileStage.Value): Action[IngestPayload] = Action.async(parse.json[IngestPayload]) { implicit request =>
    storage.streamFiles(bucket, Some(prefix(id, ds, stage)))
      .map(_.key.replace(prefix(id, ds, stage), ""))
      .runWith(Sink.seq).flatMap { seq =>
      ingestFiles(id, ds, stage).apply(request.withBody(request.body.copy(files = seq)))
    }
  }

  private def streamToStorage(id: String, ds: String, stage: FileStage.Value, fileName: String): BodyParser[Source[ByteString, _]] = BodyParser { implicit r =>
    Accumulator.source[ByteString]
      .mapFuture(src => storage.putBytes(bucket, s"${prefix(id, ds, stage)}$fileName", src, r.contentType))
      .map(f => Source.single(ByteString(Json.prettyPrint(Json.obj("url" -> f.toString)))))
      .map(Right.apply)
  }

  def uploadStream(id: String, ds: String, stage: FileStage.Value, fileName: String): Action[Source[ByteString, _]] =
    EditAction(id).apply(streamToStorage(id, ds, stage, fileName)) { implicit request =>
      // Upload via the server. Normally you'd PUT direct from the client
      // to the storage, but this is useful for testing
      Ok.chunked(request.body).as(ContentTypes.JSON)
    }

  def download(id: String, ds: String, stage: FileStage.Value, fileName: String, versionId: Option[String]): Action[AnyContent] = EditAction(id).async { implicit req =>
    storage.get(bucket, s"${prefix(id, ds, stage)}$fileName", versionId = versionId).map {
      case Some((meta, bytes)) =>
        Ok.chunked(bytes)
          .as(meta.contentType.getOrElse(ContentTypes.BINARY))
          .withHeaders(meta.eTag.toSeq.map(tag => HeaderNames.ETAG -> tag): _*)
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment;filename=$fileName")
      case _ => NotFound
    }
  }

  def info(id: String, ds: String, stage: FileStage.Value, fileName: String, versionId: Option[String]): Action[AnyContent] = EditAction(id).async { implicit req =>
    val pre = prefix(id, ds, stage)
    val path = pre + fileName
    for {
      Some(meta) <- storage.info(bucket, path, versionId)
      versions <- storage.listVersions(bucket, path)
    } yield Ok(Json.obj(
      "meta" -> meta.copy(key = meta.key.replace(pre, "")),
      "presignedUrl" -> storage.uri(bucket, path, 2.hours, versionId = versionId, contentType = meta.contentType),
      "versions" -> versions.files.map(v => v.copy(key = v.key.replace(pre, "")))
    ))
  }

  private def getUrlMap(keys: Seq[String]): Future[Map[String, java.net.URI]] = {
    // NB: Not doing this with regular Future.successful so as to
    // limit parallelism to the specified amount
    Source(keys.toList)
      .mapAsync(4)(path => storage.info(bucket, path).map(info => path -> info))
      .collect { case (path, Some(meta)) =>
        val id = meta.versionId.map(vid => s"$path?versionId=$vid").getOrElse(path)
        id -> storage.uri(meta.classifier, meta.key, duration = 2.hours, versionId = meta.versionId)
      }
      .runFold(Map.empty[String, java.net.URI])(_ + _)
  }

  def ingestFiles(id: String, ds: String, stage: FileStage.Value): Action[IngestPayload] = EditAction(id).async(parse.json[IngestPayload]) { implicit request =>
    import scala.concurrent.duration._
    val keys = request.body.files.map(path => s"${prefix(id, ds, stage)}$path")
    for (urls <- getUrlMap(keys); dataset <- datasets.get(id, ds)) yield {

      // Tag this task with a unique ID...
      val jobId = UUID.randomUUID().toString

      // Type is json, since it's a mapping of key -> URL
      val contentType = play.api.http.ContentTypes.JSON

      val task = IngestParams(
        scopeType = defines.ContentTypes.Repository,
        scope = id,
        allowUpdate = true,
        data = UrlMapPayload(urls),
        log = request.body.logMessage,
        tolerant = request.body.tolerant,
        commit = request.body.commit,
        properties = request.body.properties
          .map(ref => PropertiesHandle(
            storage.uri(bucket, s"${prefix(id, ds, FileStage.Config)}$ref", 2.hours).toString))
          .getOrElse(PropertiesHandle.empty)
      )
      val ingestTask = IngestData(task, IngestDataType.Ead, contentType, implicitly[ApiUser])
      val job = IngestJob(jobId, ingestTask)
      val onDone: (IngestJob, ImportLog) => Future[Unit] = (job, log) =>
        if (job.data.params.commit && log.event.isDefined) importLogService.save(id, ds, job.data, log)
        else Future.successful(())

      mat.system.actorOf(Props(ingest.DataImporterManager(job, ingestApi, onDone)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks.taskMonitorWS(jobId).webSocketURL(globalConfig.https),
        "jobId" -> jobId
      ))
    }
  }

  def uploadHandle(id: String, ds: String, stage: FileStage.Value): Action[FileToUpload] = EditAction(id).apply(parse.json[FileToUpload]) { implicit request =>
    val path = s"${prefix(id, ds, stage)}${request.body.name}"
    val url = storage.uri(bucket, path, contentType = Some(request.body.`type`))
    Ok(Json.obj("presignedUrl" -> url))
  }

  def deleteFiles(id: String, ds: String, stage: FileStage.Value): Action[Seq[String]] = EditAction(id).async(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id, ds, stage)}$path")
    storage.deleteFiles(bucket, keys: _*).map { deleted =>
      Ok(Json.toJson(deleted.map(_.replace(prefix(id, ds, stage), ""))))
    }
  }

  def deleteAll(id: String, ds: String, stage: FileStage.Value): Action[AnyContent] = EditAction(id).async { implicit request =>
    def deleteBatch(batch: Seq[FileMeta]): Future[Boolean] = {
      storage
        .deleteFiles(bucket, batch.map(_.key): _*)
        .map(_.size == batch.size)
    }

    val r: Future[Boolean] = storage
      .streamFiles(bucket, Some(prefix(id, ds, stage)))
      .grouped(200)
      .mapAsync(2)(deleteBatch)
      .runWith(Sink.seq)
      .map((s: Seq[Boolean]) => s.forall(g => g))
    r.map { ok: Boolean =>
      Ok(Json.obj("ok" -> ok))
    }
  }

  def validateEad(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.validateEad(Map.empty[String, Seq[XmlValidationError]], request.item, fileForm,
      repositoryDataRoutes.validateEadPost(id)))
  }

  def validateEadPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    request.body.asMultipartFormData.map { data =>
      val results: Seq[Future[(String, Seq[XmlValidationError])]] = data.files.map { file =>
        eadValidator.validateEad(file.ref.toPath).map(errs => file.filename -> errs)
      }

      Future.sequence(results).map { out =>
        Ok(views.html.admin.repository.validateEad(out.sortBy(_._1).toMap, request.item, fileForm,
          repositoryDataRoutes.validateEadPost(id)))
      }
    }.getOrElse {
      immediate(Redirect(repositoryDataRoutes.validateEad(id)))
    }
  }

  def harvestOaiPmh(id: String, ds: String, fromLast: Boolean): Action[OaiPmhConfig] = EditAction(id).async(parse.json[OaiPmhConfig]) { implicit request =>
    val lastHarvest: Future[Option[Instant]] =
      if (fromLast) harvestEvents.get(id, Some(ds)).map(events =>
        events
          .filter(_.eventType == HarvestEventType.Completed)
          .map(_.created)
          .lastOption
      ) else immediate(Option.empty[Instant])

    lastHarvest.map { last =>
      val endpoint = request.body
      val jobId = UUID.randomUUID().toString
      val data = OaiPmhHarvestData(endpoint, bucket, prefix = prefix(id, ds, FileStage.Input), from = last)
      val job = OaiPmhHarvestJob(id, ds, jobId, data = data)
      mat.system.actorOf(Props(OaiPmhHarvesterManager(job, oaipmhClient, storage, harvestEvents)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks
          .taskMonitorWS(jobId).webSocketURL(globalConfig.https),
        "jobId" -> jobId
      ))
    }
  }

  def cancelOaiPmhHarvest(id: String, jobId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    mat.system.actorSelection("user/" + jobId).resolveOne(5.seconds).map { ref =>
      logger.info(s"Monitoring job: $jobId")
      ref ! OaiPmhHarvester.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def getOaiPmhConfig(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    oaipmhConfigs.get(id, ds).map { opt =>
      Ok(Json.toJson(opt))
    }
  }

  def saveOaiPmhConfig(id: String, ds: String): Action[OaiPmhConfig] = EditAction(id).async(parse.json[OaiPmhConfig]) { implicit request =>
    oaipmhConfigs.save(id, ds, request.body).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def deleteOaiPmhConfig(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    oaipmhConfigs.delete(id, ds).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def testOaiPmhConfig(id: String, ds: String): Action[OaiPmhConfig] = EditAction(id).async(parse.json[OaiPmhConfig]) { implicit request =>
    val getIdentF = oaipmhClient.identify(request.body)
    val listIdentF = oaipmhClient.listIdentifiers(request.body)
    (for (ident <- getIdentF; _ <- listIdentF)
      yield Ok(Json.toJson(ident))).recover {
      case e: OaiPmhError => BadRequest(Json.obj("error" -> e.errorMessage))
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def getConvertConfig(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.getConfig(id, ds).map(dts => Ok(Json.toJson(dts)))
  }

  def saveConvertConfig(id: String, ds: String): Action[Seq[String]] = EditAction(id).async(parse.json[Seq[String]]) { implicit request =>
    dataTransformations.saveConfig(id, ds, request.body).map(_ => Ok(Json.toJson("ok" -> true)))
  }

  def listDataTransformations(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.list()
      .map(_.filter(dt => dt.repoId.isEmpty || dt.repoId.contains(id)))
      .map(dts => Ok(Json.toJson(dts)))
  }

  def getDataTransformation(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.get(dtId).map(dt => Ok(Json.toJson(dt)))
  }

  def createDataTransformation(id: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(parse.json[DataTransformationInfo]) { implicit request =>
    dataTransformations.create(request.body, if (generic) None else Some(id)).map { dt =>
      Ok(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def updateDataTransformation(id: String, dtId: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(parse.json[DataTransformationInfo]) { implicit request =>
    dataTransformations.update(dtId, request.body, if (generic) None else Some(id)).map { dt =>
      Ok(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def deleteDataTransformation(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.delete(dtId).map(ok => Ok(Json.toJson(ok)))
  }

  private def configToMappings(config: ConvertConfig): Future[Seq[(DataTransformation.TransformationType.Value, String)]] = config match {
    case TransformationList(mappings) => dataTransformations.get(mappings).map(_.map(dt => dt.bodyType -> dt.body))
    case ConvertSpec(mappings) => immediate(mappings)
  }

  private def downloadAndConvertFile(path: String, mappings: Seq[(DataTransformation.TransformationType.Value, String)]): Future[String] = {
    storage.get(bucket, path).flatMap {
      case Some((_, src)) =>
        val flow = xmlTransformer.transform(mappings)
        src
          .via(flow)
          .runFold(ByteString(""))(_ ++ _)
          .map(_.utf8String)
      case None => throw new RuntimeException(s"No data found at $bucket: $path")
    }
  }

  def convertFile(id: String, ds: String, stage: FileStage.Value, fileName: String): Action[ConvertConfig] = Action.async(parse.json[ConvertConfig]) { implicit request =>
    configToMappings(request.body).flatMap { m =>
      import services.transformation.utils.digest

      // We need a recursive error handler here which, in the case of a
      // CompletionException thrown by the cache, attempts to handle the
      // underlying cause
      def errorHandler: PartialFunction[Throwable, Result] = {
        case e: CompletionException => errorHandler.apply(e.getCause)
        case e: InvalidMappingError => BadRequest(Json.toJson(e))
        case e: XmlTransformationError => BadRequest(Json.toJson(e))
      }

      val path = prefix(id, ds, stage) + fileName
      storage.info(bucket, path).flatMap {
        case Some(meta) =>
          val outF = meta.eTag match {
            // If we have an eTag for the file contents, cache the transformation against it
            case Some(tag) => transformCache.getOrElseUpdate(digest(tag, m))(downloadAndConvertFile(path, m))
            // Otherwise, no caching at this stage.
            case None =>
              logger.error(s"No eTag found when converting file: $path")
              downloadAndConvertFile(path, m)
          }

          outF.map(s => Ok(s).as("text/xml")).recover(errorHandler)
        case None => immediate(NotFound(path))
      }
    }
  }

  def convert(id: String, ds: String, key: Option[String]): Action[ConvertConfig] = EditAction(id).async(parse.json[ConvertConfig]) { implicit request =>
    val config = request.body
    configToMappings(config).map { ts =>
      val jobId = UUID.randomUUID().toString
      val data = XmlConvertData(
        ts,
        bucket,
        inPrefix = prefix(id, ds, FileStage.Input),
        outPrefix = prefix(id, ds, FileStage.Output),
        only = key
      )
      val job = XmlConvertJob(repoId = id, datasetId = ds, jobId = jobId, data = data)
      mat.system.actorOf(Props(XmlConverterManager(job, xmlTransformer, storage)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks
          .taskMonitorWS(jobId).webSocketURL(globalConfig.https),
        "jobId" -> jobId
      ))
    }
  }

  def cancelConvert(id: String, jobId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    mat.system.actorSelection("user/" + jobId).resolveOne(5.seconds).map { ref =>
      logger.info(s"Monitoring job: $jobId")
      ref ! XmlConverter.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def datasetStats(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    def countInDataset(ds: String): Future[(String, Int)] = {
      val pathPrefix: String = prefix(id, ds, FileStage.Input)
      asyncCache.getOrElseUpdate(s"bucket:count:$bucket/$pathPrefix", 1.minute) {
        storage.count(bucket, Some(pathPrefix)).map(count => ds -> count)
      }
    }

    for {
      dsl <- datasets.list(id)
      idToCount <- Future.sequence(dsl.map(ds => countInDataset(ds.id)))
    } yield Ok(Json.toJson(idToCount.toMap))
  }

  def listDatasets(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    datasets.list(id).map(dsl => Ok(Json.toJson(dsl)))
  }

  def createDataset(id: String): Action[ImportDatasetInfo] = EditAction(id).async(parse.json[ImportDatasetInfo]) { implicit request =>
    datasets.create(id, request.body).map { ds =>
      Ok(Json.toJson(ds))
    }.recover {
      case e: ImportDatasetExists => BadRequest(e)
    }
  }

  def updateDataset(id: String, ds: String): Action[ImportDatasetInfo] = EditAction(id).async(parse.json[ImportDatasetInfo]) { implicit request =>
    datasets.update(id, ds, request.body).map { ds =>
      Ok(Json.toJson(ds))
    }
  }

  def deleteDataset(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    // Delete all files in stages in the dataset, then the dataset itself...
    val del: Seq[Future[Seq[String]]] = FileStage.values.toSeq
      .map(s => storage.deleteFilesWithPrefix(bucket, prefix(id, ds, s)))
    for (_ <- Future.sequence(del); ds <- datasets.delete(id, ds))
      yield Ok(Json.toJson(ds))
  }
}
