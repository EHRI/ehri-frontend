package controllers.datasets

import actors.transformation.XmlConverterManager
import actors.transformation.XmlConverterManager.{XmlConvertData, XmlConvertJob}
import akka.NotUsed
import akka.actor.Props
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Update
import eu.ehri.project.xml._
import models._
import play.api.Logger
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.libs.json.JsError.toJson
import play.api.libs.json.{JsObject, Json, Reads, Writes}
import play.api.mvc._
import services.datasets.ImportDatasetService
import services.storage.{FileMeta, FileStorage}
import services.transformation._
import services.transformation.utils.{digest, getUtf8Transcoder}

import java.util.UUID
import java.util.concurrent.CompletionException
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration.Duration

@Singleton
case class ConvertConfigs @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataTransformations: DataTransformationService,
  @Named("dam") storage: FileStorage,
  xmlTransformer: XmlTransformer,
  @NamedCache("transformer-cache") transformCache: AsyncCacheApi,
  datasetApi: ImportDatasetService,
)(implicit mat: Materializer) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {

  private val logger: Logger = Logger(classOf[ConvertConfigs])
  private val cacheTime: Duration = appComponents.config.get[Duration]("ehri.admin.dataManager.cacheExpiration")

  def get(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    dataTransformations.getConfig(id, ds).map { pairs => Ok(Json.toJson(pairs))}
  }

  def save(id: String, ds: String): Action[Seq[(String, JsObject)]] = EditAction(id).async(apiJson[Seq[(String, JsObject)]]) { implicit request =>
    dataTransformations.saveConfig(id, ds, request.body).map(_ => Ok(Json.obj("ok" -> true)))
  }

  private def configToMappings(config: ConvertConfig): Future[Seq[(TransformationType.Value, String, JsObject)]] = config match {
    case TransformationList(mappings, _) => dataTransformations.get(mappings.map(_._1)).map { dts =>
      mappings.zip(dts).map { case ((_, params), dt) => (dt.bodyType, dt.body, params) }
    }
    case ConvertSpec(mappings, _) => immediate(mappings)
  }

  private def downloadAndConvertFile(meta: FileMeta, mappings: Seq[(TransformationType.Value, String, JsObject)], contentType: Option[String]): Future[String] = {
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

  private def convertFile1(path: String, mappings: Seq[(TransformationType.Value, String, JsObject)], dataset: Option[ImportDataset]): Future[Result] = {
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

  def convertFile(id: String, ds: String, stage: FileStage.Value, fileName: String): Action[ConvertConfig] = Action.async(apiJson[ConvertConfig]) { implicit request =>
    // We need a recursive error handler here which, in the case of a
    // CompletionException thrown by the cache, attempts to handle the
    // underlying cause
    implicit val _f: Writes[InvalidMappingError] = Json.writes[InvalidMappingError]

    def errorHandler: PartialFunction[Throwable, Result] = {
      case e: CompletionException => errorHandler.apply(e.getCause)
      case e: InvalidMappingError => BadRequest(Json.toJson(e))
      case e: XsltConfigError => BadRequest(Json.toJson(e))
      case e: XmlTransformationError => BadRequest(Json.toJson(e))
    }

    for {
      mappings <- configToMappings(request.body)
      datasetOpt <- datasetApi.find(id, ds)
      path = prefix(id, ds, stage) + fileName
      result <- convertFile1(path, mappings, datasetOpt).recover(errorHandler)
    } yield result
  }

  def convert(id: String, ds: String, key: Option[String]): Action[ConvertConfig] = EditAction(id).async(apiJson[ConvertConfig]) { implicit request =>
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
}
