package controllers.institutions

import java.io.PrintWriter
import java.net.URLEncoder
import java.util.UUID

import actors.IngestActor
import akka.actor.Props
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import javax.inject._
import models._
import models.admin.OaiPmhConfig
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.ContentTypes
import play.api.i18n.Messages
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.{Format, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc._
import services.data.{ApiUser, DataHelpers}
import services.ingest.IngestApi.{IngestData, IngestJob}
import services.ingest._
import services.search._
import services.storage.{FileMeta, FileStorage}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

case class FileToUpload(name: String, `type`: String, size: Long)
object FileToUpload {
  implicit val _json: Format[FileToUpload] = Json.format[FileToUpload]
}

case class IngestPayload(
  logMessage: String,
  tolerant: Boolean = false,
  commit: Boolean = false,
  files: Seq[String] = Seq.empty
)
object IngestPayload {
  implicit val _json: Format[IngestPayload] = Json.format[IngestPayload]
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
  oaiPmhClient: OaiPmhClient
)(
  implicit mat: Materializer
) extends AdminController
  with Read[Repository]
  with Update[Repository] {
  private val logger = play.api.Logger(classOf[RepositoryData])

  private val repositoryDataRoutes = controllers.institutions.routes.RepositoryData

  private val fileForm = Form(single("file" -> text))
  private val bucket = "ehri-assets"
  private def instance(implicit request: RequestHeader): String =
    URLEncoder.encode(config.getOptional[String]("storage.instance").getOrElse(request.host), "UTF-8")
  private def prefix(id: String)(implicit request: RequestHeader): String = s"$instance/ingest/$id/"

  def manager(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.datamanager(request.item))
  }

  def validateFiles(id: String): Action[Seq[String]] = Action.async(parse.json[Seq[String]]) { implicit request =>
    val urls = request.body.map(key => key -> storage.uri(bucket, s"${prefix(id)}$key").toString)
    val results: Seq[Future[(String, Seq[XmlValidationError])]] = urls.map { case (key, url) =>
      eadValidator.validateEad(Uri(url)).map(errs => key -> errs)
    }
    Future.sequence(results).map { out =>
      Ok(Json.toJson(out.toMap))
    }.recover {
      case e => BadRequest(Json.obj("error" -> e.getMessage))
    }
  }

  def listFiles(id: String, path: Option[String], from: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket,
      prefix = Some(prefix(id) + path.getOrElse("")),
      from.map(key => s"${prefix(id)}$key"), max = 20).map { list =>
      Ok(Json.toJson(list.copy(files = list.files.map(f => f.copy(key = f.key.replace(prefix(id), ""))))))
    }
  }

  def fileUrls(id: String): Action[Seq[String]] = EditAction(id).apply(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id)}$path")
    val result = keys.map(key => key.replace(prefix(id), "") -> storage.uri(bucket, key)).toMap
    Ok(Json.toJson(result))
  }

  def ingestAll(id: String): Action[IngestPayload] = Action.async(parse.json[IngestPayload]) { implicit request =>
    storage.streamFiles(bucket, Some(prefix(id))).map(_.key.replace(prefix(id), ""))
        .runWith(Sink.seq).flatMap { seq =>
      ingestFiles(id).apply(request.withBody(request.body.copy(files = seq)))
    }
  }

  private def streamToStorage(id: String, fileName: String): BodyParser[Source[ByteString, _]] = BodyParser { implicit r =>
    Accumulator.source[ByteString]
      .mapFuture(src => storage.putBytes(bucket, s"${prefix(id)}$fileName", src, r.contentType))
      .map(f => Source.single(ByteString(Json.prettyPrint(Json.obj("url" -> f.toString)))))
      .map(Right.apply)
  }

  def uploadStream(id: String, fileName: String): Action[Source[ByteString, _]] =
    EditAction(id).apply(streamToStorage(id, fileName)) { implicit request =>
      // Upload via the server. Normally you'd PUT direct from the client
      // to the storage, but this is useful for testing
      Ok.chunked(request.body).as(ContentTypes.JSON)
    }

  def download(id: String, fileName: String): Action[AnyContent] = EditAction(id).async { implicit req =>
    storage.get(bucket, s"${prefix(id)}$fileName").map {
      case Some((meta, bytes)) => Ok.chunked(bytes).as(meta.contentType.getOrElse(ContentTypes.BINARY))
      case _ => NotFound
    }
  }

  def ingestFiles(id: String): Action[IngestPayload] = EditAction(id).async(parse.json[IngestPayload]) { implicit request =>
    val keys = request.body.files.map(path => s"${prefix(id)}$path")
    val urls = keys.map(key => key -> storage.uri(bucket, key)).toMap

    // Tag this task with a unique ID...
    val jobId = UUID.randomUUID().toString

    // Type is json, since it's a mapping of key -> URL
    val contentType = play.api.http.ContentTypes.JSON

    val temp = SingletonTemporaryFileCreator.create("ingest", ".json")
    val writer = new PrintWriter(temp.path.toString, "UTF-8")
    writer.write(Json.stringify(Json.toJson(urls)))
    writer.close()

    val task = IngestParams(
      scopeType = defines.ContentTypes.Repository,
      scope = id,
      allowUpdate = true,
      file = Some(temp),
      log = request.body.logMessage,
      tolerant = request.body.tolerant,
      commit = request.body.commit
    )

    val ingestTask = IngestData(task,
      IngestApi.IngestDataType.Ead, contentType, implicitly[ApiUser])
    val runner = mat.system.actorOf(Props(IngestActor(ingestApi)), jobId)
    runner ! IngestJob(jobId, ingestTask)

    immediate {
      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks.taskMonitorWS(jobId).webSocketURL(globalConfig.https),
        "jobId" -> jobId
      ))
    }
  }

  def uploadHandle(id: String): Action[FileToUpload] = EditAction(id).apply(parse.json[FileToUpload]) { implicit request =>
    val path = s"${prefix(id)}${request.body.name}"
    val url = storage.uri(bucket, path, contentType = Some(request.body.`type`))
    Ok(Json.obj("presignedUrl" -> url))
  }

  def deleteFiles(id: String): Action[Seq[String]] = EditAction(id).async(parse.json[Seq[String]]) { implicit request =>
    val keys = request.body.map(path => s"${prefix(id)}$path")
    storage.deleteFiles(bucket, keys: _*).map { deleted =>
      Ok(Json.toJson(deleted.map(_.replace(prefix(id), ""))))
    }
  }

  def deleteAll(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    def deleteBatch(batch: Seq[FileMeta]): Future[Boolean] = {
      storage
        .deleteFiles(bucket, batch.map(_.key): _*)
        .map(_.size == batch.size)
    }

    val r: Future[Boolean] = storage
      .streamFiles(bucket, Some(prefix(id)))
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

  private def oaiPrefix(id: String)(implicit request: RequestHeader): String = s"$instance/oaipmh/$id/"

  def harvestOaiPmh(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.harvest(request.item,
      OaiPmhConfig.form, repositoryDataRoutes.harvestOaiPmhPost(id)))
  }

  def harvestOaiPmhPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    OaiPmhConfig.form.bindFromRequest.fold(
      errs => immediate {
        if (isAjax) BadRequest(errs.errorsAsJson)
        else BadRequest(views.html.admin.repository.harvest(request.item, errs,
          repositoryDataRoutes.harvestOaiPmhPost(id)))
      },
      endpoint => oaiPmhClient
        .listIdentifiers(endpoint)
        .mapAsync(1)(id => {
          storage.putBytes(
            bucket,
            oaiPrefix(id),
            oaiPmhClient.getRecord(endpoint, id),
            Some(ContentTypes.XML)
          ).map (uri => id -> uri)
        })
        .map { case (id, uri) =>
          logger.debug(s"Harvested $id to $uri")
          uri
        }
        .runFold(0) { case (i, _) => i + 1 }
        .map { count =>
          if (isAjax) Ok(Json.toJson(count))
          else Redirect(repositoryDataRoutes.harvestOaiPmh(id))
            .flashing("success" ->
              Messages("repository.harvest.oaipmh.successCount", count))

        }
    )
  }
}
