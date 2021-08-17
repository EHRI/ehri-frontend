package controllers.datasets

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import models._
import play.api.libs.json.{JsString, Json, Reads}
import play.api.mvc._
import services.cypher.CypherService
import services.datasets.ImportDatasetService
import services.ingest.{ImportConfigService, ImportLogService}
import services.storage.FileStorage

import javax.inject._

case class SnapshotInfo(notes: Option[String])
object SnapshotInfo {
  implicit val _reads: Reads[SnapshotInfo] = Json.format[SnapshotInfo]
}


@Singleton
case class ImportLogs @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  importConfigs: ImportConfigService,
  datasets: ImportDatasetService,
  importLogService: ImportLogService,
  cypherServer: CypherService
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  def listSnapshots(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.snapshots(id).map { info =>
      Ok(Json.toJson(info))
    }
  }

  def takeSnapshot(id: String): Action[SnapshotInfo] = EditAction(id).async(parse.json[SnapshotInfo]) { implicit request =>
    val src: Source[(String, String), _] = cypherServer.rows(
      """MATCH (r: Repository {__id: $id})<-[:heldBy|childOf*]-(d:DocumentaryUnit)
         RETURN DISTINCT d.__id as id, d.identifier as local""", Map("id" -> JsString(id)))
      .collect {
        case JsString(itemId) :: JsString(localId) :: Nil => itemId -> localId
      }

    importLogService.saveSnapshot(id, src, request.body.notes).map { snapshot =>
      Ok(Json.toJson(snapshot))
    }
  }

  def diffSnapshot(id: String, snapshotId: Int): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.findUntouchedItemIds(id, snapshotId).map { items =>
      Ok(Json.toJson(items))
    }
  }
}
