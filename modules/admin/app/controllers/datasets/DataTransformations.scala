package controllers.datasets

import actors.transformation.XmlConverterManager.{XmlConvertData, XmlConvertJob}
import actors.transformation.{XmlConverter, XmlConverterManager}
import akka.NotUsed
import akka.actor.Props
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Update
import models._
import play.api.Logger
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.libs.json.JsError.toJson
import play.api.libs.json.{JsObject, Json, Reads}
import play.api.mvc._
import services.datasets.ImportDatasetService
import services.storage.{FileMeta, FileStorage}
import services.transformation._
import services.transformation.utils.digest

import java.util.UUID
import java.util.concurrent.CompletionException
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration.Duration

@Singleton
case class DataTransformations @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataTransformations: DataTransformationService,
  @Named("dam") storage: FileStorage,
  xmlTransformer: XmlTransformer,
  @NamedCache("transformer-cache") transformCache: AsyncCacheApi,
  datasetApi: ImportDatasetService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  private val logger: Logger = Logger(classOf[DataTransformations])
  private val cacheTime: Duration = appComponents.config.get[Duration]("ehri.admin.dataManager.cacheExpiration")

  // To override the max request size we unfortunately need to define our own body parser here:
  // The max value is drawn from config:
  private def json[A](implicit reader: Reads[A]): BodyParser[A] = BodyParser { request =>
    val max = config.get[Long]("ehri.admin.dataManager.maxTransformationSize")
    parse.json(max)(request).map {
      case Left(simpleResult) => Left(simpleResult)
      case Right(jsValue) =>
        jsValue.validate(reader).map { a =>
          Right(a)
        } recoverTotal { jsError =>
          Left(BadRequest(Json.obj("error" -> "invalid", "details" -> toJson(jsError))))
        }
    }
  }

  def getConfig(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.getConfig(id, ds).map { pairs => Ok(Json.toJson(pairs))}
  }

  def saveConfig(id: String, ds: String): Action[Seq[(String, JsObject)]] = EditAction(id).async(parse.json[Seq[(String, JsObject)]]) { implicit request =>
    dataTransformations.saveConfig(id, ds, request.body).map(_ => Ok(Json.toJson("ok" -> true)))
  }

  def list(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.list()
      .map(_.filter(dt => dt.repoId.isEmpty || dt.repoId.contains(id)))
      .map(dts => Ok(Json.toJson(dts)))
  }

  def get(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.get(dtId).map(dt => Ok(Json.toJson(dt)))
  }

  def create(id: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(json[DataTransformationInfo]) { implicit request =>
    dataTransformations.create(request.body, if (generic) None else Some(id)).map { dt =>
      Created(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def update(id: String, dtId: String, generic: Boolean): Action[DataTransformationInfo] = EditAction(id).async(json[DataTransformationInfo]) { implicit request =>
    dataTransformations.update(dtId, request.body, if (generic) None else Some(id)).map { dt =>
      Ok(Json.toJson(dt))
    }.recover {
      case e: DataTransformationExists => BadRequest(e)
    }
  }

  def delete(id: String, dtId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.delete(dtId).map(_ => NoContent)
  }


  private def configToMappings(config: ConvertConfig): Future[Seq[(DataTransformation.TransformationType.Value, String, JsObject)]] = config match {
    case TransformationList(mappings, _) => dataTransformations.get(mappings.map(_._1)).map { dts =>
      mappings.zip(dts).map { case ((_, params), dt) => (dt.bodyType, dt.body, params) }
    }
    case ConvertSpec(mappings, _) => immediate(mappings)
  }

  private def downloadAndConvertFile(meta: FileMeta, mappings: Seq[(DataTransformation.TransformationType.Value, String, JsObject)], contentType: Option[String]): Future[String] = {
    import services.transformation.utils.getUtf8Transcoder

    storage.get(meta.key).flatMap {
      case Some((_, src)) =>
        val transcoder: Option[Flow[ByteString, ByteString, NotUsed]] = getUtf8Transcoder(contentType.orElse(meta.contentType))
        val utf8src: Source[ByteString, _] = transcoder.map(tc => src.via(tc)).getOrElse(src)
        utf8src
          .via(xmlTransformer.transform(mappings))
          .runFold(ByteString.empty)(_ ++ _)
          .map(_.utf8String)
      case None => throw new RuntimeException(s"No data found at ${storage.name}: ${meta.key}")
    }
  }

  private def convertFile1(path: String, mappings: Seq[(DataTransformation.TransformationType.Value, String, JsObject)], dataset: Option[ImportDataset]): Future[Result] = {
    storage.info(path).flatMap {
      case Some((meta, _)) =>
        // The dataset content type overrides the file info, allowing it to be
        // made more specific with the addition of a non-UTF-8 charset
        val contentType = dataset.flatMap(_.contentType).orElse(meta.contentType)

        val data: Future[String] = meta.eTag match {
          // If we have an eTag for the file contents, cache the transformation against it
          case Some(tag) =>
            transformCache.getOrElseUpdate(digest(tag, mappings), cacheTime)(downloadAndConvertFile(meta, mappings, contentType))
          // Otherwise, no caching at this stage.
          case None =>
            logger.error(s"No eTag found when converting file: $path")
            downloadAndConvertFile(meta, mappings, contentType)
        }
        data.map(Ok(_).as("text/xml"))

      case None => immediate(NotFound(path))
    }
  }

  def convertFile(id: String, ds: String, stage: FileStage.Value, fileName: String): Action[ConvertConfig] = Action.async(json[ConvertConfig]) { implicit request =>
    // We need a recursive error handler here which, in the case of a
    // CompletionException thrown by the cache, attempts to handle the
    // underlying cause
    def errorHandler: PartialFunction[Throwable, Result] = {
      case e: CompletionException => errorHandler.apply(e.getCause)
      case e: InvalidMappingError => BadRequest(Json.toJson(e))
      case e: XmlTransformationError => BadRequest(Json.toJson(e))
    }

    for {
      mappings <- configToMappings(request.body)
      datasetOpt <- datasetApi.find(id, ds)
      path = prefix(id, ds, stage) + fileName
      result <- convertFile1(path, mappings, datasetOpt).recover(errorHandler)
    } yield result
  }

  def convert(id: String, ds: String, key: Option[String]): Action[ConvertConfig] = EditAction(id).async(json[ConvertConfig]) { implicit request =>
    val config = request.body
    for (ts <- configToMappings(config); datasetOpt <- datasetApi.find(id, ds)) yield {
      val jobId = UUID.randomUUID().toString
      val data = XmlConvertData(
        ts,
        inPrefix = prefix(id, ds, FileStage.Input),
        outPrefix = prefix(id, ds, FileStage.Output),
        only = key,
        force = config.force,
        contentType = datasetOpt.flatMap(_.contentType),
      )
      val job = XmlConvertJob(repoId = id, datasetId = ds, jobId = jobId, data = data)
      mat.system.actorOf(Props(XmlConverterManager(job, xmlTransformer, storage)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks
          .taskMonitorWS(jobId).webSocketURL(conf.https),
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
}
