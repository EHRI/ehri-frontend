package controllers.datasets

import actors.cleanup.CleanupRunner.CleanupJob
import actors.cleanup.{CleanupRunner, CleanupRunnerManager}
import akka.actor.{ActorContext, ActorRef, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import controllers.generic._
import models._
import play.api.libs.json.{Format, JsString, Json, Reads}
import play.api.mvc._
import services.cypher.CypherService
import services.data.EventForwarder
import services.ingest.{ImportLogService, IngestService}
import services.storage.FileStorage

import java.util.UUID
import javax.inject._
import scala.concurrent.Future

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
  @Named("event-forwarder") eventForwarder: ActorRef,
  appComponents: AppComponents,
  importLogService: ImportLogService,
  cypherServer: CypherService,
  importService: IngestService,
)(implicit mat: Materializer) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {

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

  def takeSnapshot(id: String): Action[SnapshotInfo] = EditAction(id).async(apiJson[SnapshotInfo]) { implicit request =>
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

  def doCleanup(id: String, snapshotId: Int): Action[CleanupConfirmation] = EditAction(id).async(apiJson[CleanupConfirmation]) { implicit request =>
    logger.info(s"Starting cleanup for repository $id, snapshot $snapshotId")

    // Function to recursively perform a large batch deletion if over a particular threshold. This avoids
    // overloading the backend.
    val maxDeletions = config.get[Int]("ehri.admin.bulkOperations.maxDeletions")
    def deleteBatches(ids: Seq[String]): Future[Int] = {
      val (batch, rest) = ids.splitAt(maxDeletions)
      userDataApi.batchDelete(batch, Some(id), logMsg = request.body.msg,
          version = true, tolerant = true, commit = true).flatMap { count =>
        if (rest.isEmpty) Future.successful(count)
        else deleteBatches(rest).map(_ + count)
      }
    }

    for {
      cleanup <- importLogService.cleanup(id, snapshotId)
      _ = logger.info(s"Relink: ${cleanup.redirects.size}, deletions: ${cleanup.deletions.size}")
      relinkCount  <- userDataApi.relinkTargets(cleanup.redirects, tolerant = true, commit = true).map(_.map(_._3).sum)
      _ = logger.info(s"Done relinks: $relinkCount")
      redirectCount <- importService.remapMovedUnits(cleanup.redirects)
      _ = logger.info(s"Done redirects: $redirectCount")
      delCount <- deleteBatches(cleanup.deletions)
      _ = eventForwarder ! EventForwarder.Delete(cleanup.deletions)
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

  def doCleanupAsync(id: String, snapshotId: Int): Action[CleanupConfirmation] = EditAction(id).apply(apiJson[CleanupConfirmation]) { implicit request =>
    logger.info(s"Starting async cleanup for repository $id, snapshot $snapshotId")
    val jobId = UUID.randomUUID().toString
    val job = CleanupJob(id, snapshotId, jobId, request.body.msg)
    val init = (context: ActorContext) => context.actorOf(Props(CleanupRunner(userDataApi, importLogService, importService, eventForwarder)))
    mat.system.actorOf(Props(CleanupRunnerManager(job, init)), jobId)

    Ok(Json.obj(
      "url" -> controllers.admin.routes.Tasks.taskMonitorWS(jobId).webSocketURL(conf.https),
      "jobId" -> jobId
    ))
  }
}
