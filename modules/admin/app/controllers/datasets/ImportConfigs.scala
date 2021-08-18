package controllers.datasets

import actors.ingest
import akka.actor.Props
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import controllers.AppComponents
import controllers.base.AdminController
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
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  def get(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    importConfigs.get(id, ds).map { opt =>
      Ok(Json.toJson(opt))
    }
  }

  def save(id: String, ds: String): Action[ImportConfig] = EditAction(id).async(parse.json[ImportConfig]) { implicit request =>
    importConfigs.save(id, ds, request.body).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    importConfigs.delete(id, ds).map(_ => NoContent)
  }

  def ingestFiles(id: String, ds: String): Action[IngestPayload] = EditAction(id).async(parse.json[IngestPayload]) { implicit request =>
    import scala.concurrent.duration._

    val urlsF = getUrlMap(request.body, prefix(id, ds, FileStage.Output))
    for (urls <- urlsF; dataset <- datasets.get(id, ds)) yield {

      val task = IngestParams(
        scopeType = models.ContentTypes.Repository,
        scope = id,
        data = UrlMapPayload(urls),
        allowUpdate = request.body.config.allowUpdates,
        useSourceId = request.body.config.useSourceId,
        log = request.body.config.logMessage,
        tolerant = request.body.config.tolerant,
        lang = request.body.config.defaultLang,
        commit = request.body.commit,
        properties = request.body.config.properties.map(ref =>
          PropertiesHandle(storage.uri(s"${prefix(id, ds, FileStage.Config)}$ref", 2.hours).toString))
          .getOrElse(PropertiesHandle.empty),
        fonds = dataset.fonds
      )
      // Tag this task with a unique ID...
      val jobId = UUID.randomUUID().toString

      // Type is json, since it's a mapping of key -> URL
      val contentType = play.api.http.ContentTypes.JSON

      // Use the sync endpoint if this fonds is synced and we a) are doing the complete
      // set and b) have a fonds.
      // Don't allow sync on the repository scope, because it is too dangerous.
      val idt = if (dataset.sync && request.body.files.isEmpty && dataset.fonds.isDefined)
        IngestDataType.EadSync else IngestDataType.Ead

      val ingestTask = IngestData(task, idt, contentType, implicitly[DataUser], instance)
      val job = IngestJob(jobId, ingestTask)
      val onDone: (IngestJob, ImportLog) => Future[Unit] = (job, log) =>
        if (job.data.params.commit) importLogService.save(id, ds, job.data, log)
        else Future.successful(())

      mat.system.actorOf(Props(ingest.DataImporterManager(job, ingestService, onDone)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks.taskMonitorWS(jobId).webSocketURL(conf.https),
        "jobId" -> jobId
      ))
    }
  }

  private def getUrlMap(data: IngestPayload, prefix: String): Future[Map[String, java.net.URI]] = {
    // NB: Not doing this with regular Future.successful so as to
    // limit parallelism to the specified amount
    import scala.concurrent.duration._
    val keys = if (data.files.isEmpty) storage.streamFiles(Some(prefix)).map(_.key)
    else Source(data.files.map(prefix + _).toList)

    keys
      .mapAsync(4)(path => storage.info(path).map(info => path -> info))
      .collect { case (path, Some((meta, _))) =>
        val id = meta.versionId.map(vid => s"$path?versionId=$vid").getOrElse(path)
        id -> storage.uri(meta.key, duration = 2.hours, versionId = meta.versionId)
      }
      .runFold(Map.empty[String, java.net.URI])(_ + _)
  }
}
