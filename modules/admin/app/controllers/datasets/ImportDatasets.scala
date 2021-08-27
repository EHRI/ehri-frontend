package controllers.datasets

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import models.{FileStage, _}
import play.api.cache.AsyncCacheApi
import play.api.http.MimeTypes
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import services.datasets.{ImportDatasetExists, ImportDatasetService}
import services.ingest.ImportLogService
import services.storage.FileStorage

import javax.inject._
import scala.concurrent.Future


case class RepositoryDatasets(repoId: String, name: String, sets: Seq[ImportDataset])
object RepositoryDatasets {
  implicit val _format: Format[RepositoryDatasets] = Json.format[RepositoryDatasets]
}

@Singleton
case class ImportDatasets @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  @Named("dam") storage: FileStorage,
  datasets: ImportDatasetService,
  asyncCache: AsyncCacheApi,
  importLogService: ImportLogService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  def jsRoutes(): Action[AnyContent] = Action.apply { implicit request =>
    Ok(
      JavaScriptReverseRouter("datasetApi")(
        controllers.admin.routes.javascript.Tasks.taskMonitorWS,
        controllers.datasets.routes.javascript.LongRunningJobs.cancel,
        controllers.datasets.routes.javascript.ImportFiles.listFiles,
        controllers.datasets.routes.javascript.ImportFiles.info,
        controllers.datasets.routes.javascript.ImportFiles.validateFiles,
        controllers.datasets.routes.javascript.ImportFiles.deleteFiles,
        controllers.datasets.routes.javascript.ImportFiles.uploadHandle,
        controllers.datasets.routes.javascript.ImportFiles.fileUrls,
        controllers.datasets.routes.javascript.OaiPmhConfigs.harvest,
        controllers.datasets.routes.javascript.OaiPmhConfigs.get,
        controllers.datasets.routes.javascript.OaiPmhConfigs.save,
        controllers.datasets.routes.javascript.OaiPmhConfigs.delete,
        controllers.datasets.routes.javascript.OaiPmhConfigs.test,
        controllers.datasets.routes.javascript.ResourceSyncConfigs.sync,
        controllers.datasets.routes.javascript.ResourceSyncConfigs.get,
        controllers.datasets.routes.javascript.ResourceSyncConfigs.save,
        controllers.datasets.routes.javascript.ResourceSyncConfigs.delete,
        controllers.datasets.routes.javascript.ResourceSyncConfigs.test,
        controllers.datasets.routes.javascript.ResourceSyncConfigs.clean,
        controllers.datasets.routes.javascript.ConvertConfigs.convertFile,
        controllers.datasets.routes.javascript.ConvertConfigs.convert,
        controllers.datasets.routes.javascript.ConvertConfigs.get,
        controllers.datasets.routes.javascript.ConvertConfigs.save,
        controllers.datasets.routes.javascript.DataTransformations.list,
        controllers.datasets.routes.javascript.DataTransformations.get,
        controllers.datasets.routes.javascript.DataTransformations.create,
        controllers.datasets.routes.javascript.DataTransformations.update,
        controllers.datasets.routes.javascript.DataTransformations.delete,
        controllers.datasets.routes.javascript.ImportDatasets.list,
        controllers.datasets.routes.javascript.ImportDatasets.listAll,
        controllers.datasets.routes.javascript.ImportDatasets.stats,
        controllers.datasets.routes.javascript.ImportDatasets.create,
        controllers.datasets.routes.javascript.ImportDatasets.update,
        controllers.datasets.routes.javascript.ImportDatasets.delete,
        controllers.datasets.routes.javascript.ImportDatasets.batch,
        controllers.datasets.routes.javascript.ImportDatasets.errors,
        controllers.datasets.routes.javascript.ImportConfigs.ingestFiles,
        controllers.datasets.routes.javascript.ImportConfigs.get,
        controllers.datasets.routes.javascript.ImportConfigs.save,
        controllers.datasets.routes.javascript.ImportConfigs.delete,
        controllers.datasets.routes.javascript.ImportLogs.listSnapshots,
        controllers.datasets.routes.javascript.ImportLogs.takeSnapshot,
        controllers.datasets.routes.javascript.ImportLogs.diffSnapshot,
        controllers.datasets.routes.javascript.ImportLogs.cleanup,
        controllers.datasets.routes.javascript.CoreferenceTables.getTable,
        controllers.datasets.routes.javascript.CoreferenceTables.saveTable,
        controllers.datasets.routes.javascript.CoreferenceTables.ingestTable,
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  def ui(): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    for {
      sets <- datasets.listAll().map(_.toSeq)
      repos <- userDataApi.fetch[Repository](sets.map(_._1))
      combined = sets.zip(repos).collect{ case ((id, sets), Some(r)) => (id, r.toStringLang, sets)}
    } yield {
      Ok(views.html.admin.datasets.datasets(combined))
    }
  }

  def manager(id: String, ds: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.isVersioned.map { versioned =>
      Ok(views.html.admin.datasets.manager(request.item, versioned))
    }
  }

  def toggleVersioning(id: String, enabled: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    storage.setVersioned(enabled).map { _ =>
      Redirect(controllers.datasets.routes.ImportDatasets.manager(id))
    }
  }

  def stats(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    def countInDataset(ds: String): Future[(String, Int)] = {
      val pathPrefix: String = prefix(id, ds, FileStage.Input)
      asyncCache.getOrElseUpdate(s"bucket:count:${storage.name}/$pathPrefix", 10.seconds) {
        storage.count(Some(pathPrefix)).map(count => ds -> count)
      }
    }

    for {
      dsl <- datasets.list(id)
      idToCount <- Future.sequence(dsl.map(ds => countInDataset(ds.id)))
    } yield Ok(Json.toJson(idToCount.toMap))
  }

  def checkAll(): Action[AnyContent] = Action.async { implicit request =>
    ???
  }

  def list(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    datasets.list(id).map(dsl => Ok(Json.toJson(dsl)))
  }

  def listAll(): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    for {
      sets <- datasets.listAll().map(_.toSeq)
      repos <- userDataApi.fetch[Repository](sets.map(_._1))
      combined = sets.zip(repos).collect {
        case ((id, sets), Some(r)) => RepositoryDatasets(id, r.toStringLang, sets)
      }
    } yield Ok(Json.toJson(combined))
  }

  def create(id: String): Action[ImportDatasetInfo] = EditAction(id).async(parse.json[ImportDatasetInfo]) { implicit request =>
    datasets.create(id, request.body).map { ds =>
      Created(Json.toJson(ds))
    }.recover {
      case e: ImportDatasetExists => BadRequest(e)
    }
  }

  def update(id: String, ds: String): Action[ImportDatasetInfo] = EditAction(id).async(parse.json[ImportDatasetInfo]) { implicit request =>
    datasets.update(id, ds, request.body).map { ds =>
      Ok(Json.toJson(ds))
    }
  }

  def batch(id: String): Action[Seq[ImportDatasetInfo]] = EditAction(id).async(parse.json[Seq[ImportDatasetInfo]]) { implicit request =>
    datasets.batch(id, request.body).map { _ =>
      Ok(Json.obj("ok" -> true))
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    // Delete all files in stages in the dataset, then the dataset itself...
    val del: Seq[Future[Seq[String]]] = FileStage.values.toSeq
      .map(s => storage.deleteFilesWithPrefix(prefix(id, ds, s)))
    for (_ <- Future.sequence(del); _ <- datasets.delete(id, ds))
      yield NoContent
  }

  def errors(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.errors(id, ds).map { errs =>
      Ok(Json.toJson(errs.map { case (key, err) =>
        key.replace(prefix(id, ds, FileStage.Output), "") -> key
      }))
    }
  }
}
