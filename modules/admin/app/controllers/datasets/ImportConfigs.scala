package controllers.datasets

import actors.ingest
import org.apache.pekko.actor.Props
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import controllers.generic._
import models._
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import services.data.DataUser
import services.datasets.ImportDatasetService
import services.ingest.IngestService.{IngestData, IngestDataType, IngestJob}
import services.ingest.{ImportConfigService, ImportLogService, IngestService}
import services.storage.FileStorage

import java.util.UUID
import javax.inject._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration


case class IngestPayload(
  config: ImportConfig,
  commit: Boolean = false,
  files: Seq[String] = Seq.empty
)

object IngestPayload {
  implicit val _json: Format[IngestPayload] = Json.format[IngestPayload]
}


@Singleton
case class ImportConfigs @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  importConfigs: ImportConfigService,
  datasets: ImportDatasetService,
  importLogService: ImportLogService,
  ingestService: IngestService,
)(implicit mat: Materializer) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {
  private val urlExpiration: FiniteDuration = config.get[FiniteDuration](
    "ehri.admin.dataManager.importUrlExpiration")

  def get(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    importConfigs.get(id, ds).map { opt =>
      Ok(Json.toJson(opt))
    }
  }

  def save(id: String, ds: String): Action[ImportConfig] = EditAction(id).async(apiJson[ImportConfig]) { implicit request =>
    importConfigs.save(id, ds, request.body).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    importConfigs.delete(id, ds).map(_ => NoContent)
  }

  def ingestFiles(id: String, ds: String): Action[IngestPayload] = EditAction(id).async(apiJson[IngestPayload]) { implicit request =>
    val urlsF = getUrlMap(request.body, prefix(id, ds, FileStage.Output))
    for (urls <- urlsF; dataset <- datasets.get(id, ds)) yield {

      println(s"URL Map: $urls")

      val scopeType = if (dataset.fonds.isDefined && dataset.nest)
        models.ContentTypes.DocumentaryUnit else models.ContentTypes.Repository

      val scopeId = if (dataset.fonds.isDefined && dataset.nest)
        dataset.fonds.get else id

      // In the normal case this will be a list of one item, but
      // if batchSize is set, it will be a list of batches.
      val params: List[IngestParams] = urls.map { urlBatch =>
        IngestParams(
          scopeType = scopeType,
          scope = scopeId,
          data = UrlMapPayload(urlBatch),
          allowUpdate = request.body.config.allowUpdates,
          useSourceId = request.body.config.useSourceId,
          log = request.body.config.logMessage,
          tolerant = request.body.config.tolerant,
          lang = request.body.config.defaultLang,
          commit = request.body.commit,
          properties = request.body.config.properties.map(ref =>
              PropertiesHandle(storage.uri(s"${prefix(id, ds, FileStage.Config)}$ref", urlExpiration).toString))
            .getOrElse(PropertiesHandle.empty),
          hierarchyFile = request.body.config.hierarchyFile.map(ref =>
            storage.uri(s"${prefix(id, ds, FileStage.Config)}$ref", urlExpiration).toString
          ),
          fonds = dataset.fonds
        )
      }
      // Tag this task with a unique ID...
      val jobId = UUID.randomUUID().toString

      // Type is json, since it's a mapping of key -> URL
      val contentType = play.api.http.ContentTypes.JSON

      // Use the sync endpoint if this fonds is synced and we a) are doing the complete
      // set and b) have a fonds.
      // Don't allow sync on the repository scope, because it is too dangerous.
      val taskType = if (dataset.fonds.isDefined && request.body.files.isEmpty && dataset.sync)
        IngestDataType.EadSync else IngestDataType.Ead

      val ingestTasks = params.zipWithIndex.map { case (batchParams, i) =>
        val batchNum = if (params.size > 1) Some(i + 1) else None
        IngestData(batchParams, taskType, contentType, implicitly[DataUser], instance, batchNum)
      }
      val job = IngestJob(jobId, ingestTasks, batchSize = request.body.config.batchSize)
      val onDone: (IngestData, ImportLog) => Future[Unit] = (data, log) =>
        if (data.params.commit) {
          importLogService.save(id, ds, data, log)
        }
        else Future.successful(())

      mat.system.actorOf(Props(ingest.DataImporterManager(job, ingestService, onDone)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks.taskMonitorWS(jobId).webSocketURL(conf.https),
        "jobId" -> jobId
      ))
    }
  }

  private def getUrlMap(data: IngestPayload, prefix: String): Future[List[Map[String, java.net.URI]]] = {
    // NB: Not doing this with regular Future.successful in order to
    // limit parallelism to the specified amount
    val keys = if (data.files.isEmpty) storage.streamFiles(Some(prefix)).map(_.key)
    else Source(data.files.map(prefix + _).toList)

    val files: Future[Map[String, java.net.URI]] = keys
      .mapAsync(4)(path => storage.info(path).map(info => path -> info))
      .collect { case (path, Some((meta, _))) =>
        val id = meta.versionId.map(vid => s"$path?versionId=$vid").getOrElse(path)
        id -> storage.uri(meta.key, duration = urlExpiration, versionId = meta.versionId)
      }
      .runFold(Map.empty[String, java.net.URI])(_ + _)

    data.config.batchSize.map { size =>
      files.map(_.toSeq.sorted.grouped(size).toList.map(_.toMap))
    }.getOrElse(files.map(List(_)))
  }
}
