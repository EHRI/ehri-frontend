package controllers.datasets

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import models.{FileStage, _}
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{Format, Json}
import play.api.mvc._
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
      Ok(views.html.admin.datasets.datamanager(request.item, versioned))
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
