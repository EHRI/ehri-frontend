package controllers.institutions

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.FileStage
import javax.inject._
import models._
import play.api.cache.AsyncCacheApi
import play.api.libs.json.Json
import play.api.mvc._
import services.datasets.{ImportDatasetExists, ImportDatasetService}
import services.storage.FileStorage

import scala.concurrent.Future


@Singleton
case class ImportDatasets @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  @Named("dam") storage: FileStorage,
  datasets: ImportDatasetService,
  asyncCache: AsyncCacheApi,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  def stats(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
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

  def list(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    datasets.list(id).map(dsl => Ok(Json.toJson(dsl)))
  }

  def create(id: String): Action[ImportDatasetInfo] = EditAction(id).async(parse.json[ImportDatasetInfo]) { implicit request =>
    datasets.create(id, request.body).map { ds =>
      Ok(Json.toJson(ds))
    }.recover {
      case e: ImportDatasetExists => BadRequest(e)
    }
  }

  def update(id: String, ds: String): Action[ImportDatasetInfo] = EditAction(id).async(parse.json[ImportDatasetInfo]) { implicit request =>
    datasets.update(id, ds, request.body).map { ds =>
      Ok(Json.toJson(ds))
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    // Delete all files in stages in the dataset, then the dataset itself...
    val del: Seq[Future[Seq[String]]] = FileStage.values.toSeq
      .map(s => storage.deleteFilesWithPrefix(bucket, prefix(id, ds, s)))
    for (_ <- Future.sequence(del); ds <- datasets.delete(id, ds))
      yield Ok(Json.toJson(ds))
  }
}
