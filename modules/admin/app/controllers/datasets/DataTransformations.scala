package controllers.datasets

import actors.transformation.XmlConverterManager.{XmlConvertData, XmlConvertJob}
import actors.transformation.{XmlConverter, XmlConverterManager}
import akka.actor.Props
import akka.stream.Materializer
import akka.util.ByteString
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Update
import models._
import play.api.Logger
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.libs.json.JsError.toJson
import play.api.libs.json.{Json, Reads}
import play.api.mvc._
import services.storage.FileStorage
import services.transformation._

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
  @NamedCache("transformer-cache") transformCache: AsyncCacheApi
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
    dataTransformations.getConfig(id, ds).map(dts => Ok(Json.toJson(dts)))
  }

  def saveConfig(id: String, ds: String): Action[Seq[String]] = EditAction(id).async(parse.json[Seq[String]]) { implicit request =>
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


  private def configToMappings(config: ConvertConfig): Future[Seq[(DataTransformation.TransformationType.Value, String)]] = config match {
    case TransformationList(mappings, _) => dataTransformations.get(mappings).map(_.map(dt => dt.bodyType -> dt.body))
    case ConvertSpec(mappings, _) => immediate(mappings)
  }

  private def downloadAndConvertFile(path: String, mappings: Seq[(DataTransformation.TransformationType.Value, String)]): Future[String] = {
    storage.get(path).flatMap {
      case Some((_, src)) =>
        val flow = xmlTransformer.transform(mappings)
        src
          .via(flow)
          .runFold(ByteString(""))(_ ++ _)
          .map(_.utf8String)
      case None => throw new RuntimeException(s"No data found at ${storage.name}: $path")
    }
  }

  def convertFile(id: String, ds: String, stage: FileStage.Value, fileName: String): Action[ConvertConfig] = Action.async(json[ConvertConfig]) { implicit request =>
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
      storage.info(path).flatMap {
        case Some((meta, _)) =>
          val outF = meta.eTag match {
            // If we have an eTag for the file contents, cache the transformation against it
            case Some(tag) => transformCache.getOrElseUpdate(digest(tag, m), cacheTime)(downloadAndConvertFile(path, m))
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

  def convert(id: String, ds: String, key: Option[String]): Action[ConvertConfig] = EditAction(id).async(json[ConvertConfig]) { implicit request =>
    val config = request.body
    configToMappings(config).map { ts =>
      val jobId = UUID.randomUUID().toString
      val data = XmlConvertData(
        ts,
        inPrefix = prefix(id, ds, FileStage.Input),
        outPrefix = prefix(id, ds, FileStage.Output),
        only = key,
        force = config.force
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
