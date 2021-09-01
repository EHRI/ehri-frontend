package controllers.datasets

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import models._
import play.api.Logger
import play.api.libs.json.{Format, JsString, Json, Reads}
import play.api.mvc._
import services.cypher.CypherService
import services.ingest.{ImportLogService, IngestService}
import services.storage.FileStorage

import javax.inject._

case class SnapshotInfo(notes: Option[String])

object SnapshotInfo {
  implicit val _reads: Reads[SnapshotInfo] = Json.format[SnapshotInfo]
}

case class CleanupConfirmation(msg: String)
object CleanupConfirmation {
  implicit val _format: Format[CleanupConfirmation] = Json.format[CleanupConfirmation]
}

case class CleanupSummary(deletions: Int, relinks: Int, redirects: Int)
object CleanupSummary {
  implicit val _format: Format[CleanupSummary] = Json.format[CleanupSummary]
}


@Singleton
case class ImportLogs @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  importLogService: ImportLogService,
  cypherServer: CypherService,
  importService: IngestService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  private val logger = Logger(classOf[ImportLogs])

  def list(id: String, dsId: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.list(id, dsId).map { stats =>
      Ok(Json.toJson(stats))
    }
  }

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

  def deleteSnapshot(id: String, snapshotId: Int): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.deleteSnapshot(id, snapshotId).map { count =>
      Ok(Json.obj("deleted" -> count))
    }
  }

  def diffSnapshot(id: String, snapshotId: Int): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.findUntouchedItemIds(id, snapshotId).map { items =>
      Ok(Json.toJson(items))
    }
  }

  def cleanup(id: String, snapshotId: Int): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.cleanup(id, snapshotId).map { items =>
      Ok(Json.toJson(items))
    }
  }

  def getCleanup(id: String, snapshotId: Int, cleanupId: Int): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.getCleanup(id, snapshotId, cleanupId).map { items =>
      Ok(Json.toJson(items))
    }
  }

  def listCleanups(id: String, snapshotId: Int): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.listCleanups(id, snapshotId).map { items =>
      Ok(Json.toJson(items))
    }
  }

  def doCleanup(id: String, snapshotId: Int): Action[CleanupConfirmation] = EditAction(id).async(parse.json[CleanupConfirmation]) { implicit request =>
    logger.info(s"Starting cleanup for repository $id, snapshot $snapshotId")
    for {
      cleanup <- importLogService.cleanup(id, snapshotId)
      _ = logger.info(s"Relink: ${cleanup.redirects.size}, deletions: ${cleanup.deletions.size}")
      relinkCount  <- userDataApi.relinkTargets(cleanup.redirects, tolerant = true, commit = true).map(_.map(_._3).sum)
      _ = logger.info(s"Done relinks: $relinkCount")
      redirectCount <- importService.remapMovedUnits(cleanup.redirects)
      _ = logger.info(s"Done redirects: $redirectCount")
      delCount <- userDataApi.batchDelete(cleanup.deletions, Some(id), logMsg = request.body.msg,
          version = true, tolerant = true, commit = true)
      _ = logger.info(s"Done deletions: $delCount")
      _ <- importLogService.saveCleanup(id, snapshotId, cleanup)
    } yield {
      val sum = CleanupSummary(
        deletions = delCount,
        relinks = relinkCount,
        redirects = redirectCount,
      )
      Ok(Json.toJson(sum))
    }
  }
}
