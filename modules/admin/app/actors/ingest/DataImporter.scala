package actors.ingest

import actors.Ticker
import actors.ingest.DataImporter._
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import org.apache.pekko.pattern._
import models._
import services.ingest.IngestService.{IngestData, IngestJob}
import services.ingest._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}


object DataImporter {
  sealed trait State
  case object Start extends State
  case object ImportBatch extends State
  case object NextBatch extends State
  case class Done(start: Instant) extends State
  case class Message(msg: String) extends State
  case class UnexpectedError(throwable: Throwable) extends State
}

case class DataImporter(
  job: IngestJob,
  ingestApi: IngestService,
  onDone: (IngestData, ImportLog) => Future[Unit]
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  // Helper actor to wrap strings in a Message object
  // and forward them to this actor's manager/parent. This
  // is useful when an existing API expects an ActorRef to
  // receive plain strings to log somewhere.
  case class Forwarder(to: ActorRef) extends Actor {
    override def receive: Receive = {
      case s: String => to ! Message(s)
    }
  }

  override def receive: Receive = {
    case Start =>
      val msgTo = sender()
      msgTo ! Message(s"Initialising ingest for job: ${job.jobId}...")
      job.batchSize.foreach { batchSize =>
        msgTo ! Message(s"Splitting job into ${job.data.size} batches of $batchSize")
      }

      // Importer initializing...
      val msgForwarder = context.actorOf(Props(Forwarder(msgTo)))
      context.become(running(msgTo, job.data.head, job.data.tail, msgForwarder, Instant.now()))
      self ! ImportBatch
  }

  def running(msgTo: ActorRef, data: IngestData, todo: Seq[IngestData], forwarder: ActorRef, start: Instant): Receive = {
    case ImportBatch =>
      context.become(running(msgTo, data, todo, forwarder, start))

      data.batch.foreach { idx =>
        msgTo ! Message(s"Starting batch $idx")
      }
      // Start a ticker to show progress during the import...
      // This just sends a heartbeat whilst the import future
      // is running.
      val ticker: ActorRef = context.actorOf(Props(Ticker()))
      ticker ! (msgTo -> "Ingesting")
      ingestApi
        .importData(data)
        .pipeTo(self)
        .onComplete { _ => ticker ! Ticker.Stop }

    case sync: SyncLog =>
      msgTo ! Message(s"Sync: moved: ${sync.moved.size}, " +
        s"new: ${sync.created.size}, " +
        s"deleted: ${sync.deleted.size}")
      finish(msgTo, data, sync.log, forwarder, Some(sync))

    case log: ImportLog =>
      finish(msgTo, data, log, forwarder, None)

    case NextBatch =>
      if (todo.isEmpty) {
        msgTo ! Done(start)
      } else {
        context.become(running(msgTo, todo.head, todo.tail, forwarder, start))
        self ! ImportBatch
      }

    case e: ErrorLog =>
      msgTo ! e

    // A failed import, log save, remapping or reindexing step
    case Status.Failure(e) =>
      log.error(e, "Ingest failed")
      msgTo ! UnexpectedError(e)

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }

  // The log and file mappings are saved before the post-commit steps, so a
  // failure to remap or reindex does not lose them, but the post-commit steps
  // still run if saving the log fails.
  private def finish(msgTo: ActorRef, data: IngestData, importLog: ImportLog, forwarder: ActorRef, sync: Option[SyncLog]): Unit = {
    msgTo ! importLog
    msgTo ! Message(s"Data: created: ${importLog.created}, " +
      s"updated: ${importLog.updated}, " +
      s"unchanged: ${importLog.unchanged}, " +
      s"errors: ${importLog.errors.size}")
    importLog.event.foreach(e => msgTo ! Message(s"Event ID: $e"))
    msgTo ! Message("Uploading log...")

    val saved = for {
      _ <- onDone(data, importLog)
      url <- ingestApi.storeManifestAndLog(job.jobId, data, importLog)
    } yield msgTo ! Message(s"Log stored at $url")

    saved.transformWith { savedResult =>
      val post = if (data.params.commit) postCommit(msgTo, data, importLog, forwarder, sync).recoverWith { case e =>
        Future.failed(new Exception(s"Data was committed but post-import processing failed: ${e.getMessage}", e))
      } else {
        msgTo ! Message("Task was a dry run so not proceeding to reindex")
        Future.successful(())
      }
      post.flatMap(_ => Future.fromTry(savedResult)).map(_ => NextBatch)
    }.pipeTo(self)
  }

  private def postCommit(msgTo: ActorRef, data: IngestData, importLog: ImportLog, forwarder: ActorRef, sync: Option[SyncLog]): Future[Unit] = {
    val remapped = sync.fold(Future.successful(())) { sync =>
      msgTo ! Message("Creating redirects...")
      ingestApi
        // FIXME: derive entity type from data???
        .remapMovedUnits(EntityType.DocumentaryUnit, sync.moved.toSeq)
        .flatMap { done =>
          ingestApi.clearIndex(sync.deleted ++ sync.moved.keys.toSeq, forwarder).map { _ =>
            msgTo ! Message(s"Remapped $done item(s)")
            ingestApi.emitEvents(data, sync)
          }
        }
    }
    remapped.flatMap { _ =>
      if (importLog.hasDoneWork) {
        val updated: Seq[String] = data.params.scope +: (
          importLog.createdKeys.values.flatten.toSeq ++ importLog.updatedKeys.values.flatten.toSeq)
        msgTo ! Message("Reindexing...")
        ingestApi.emitEvents(data, importLog)
        ingestApi.reindex(updated, forwarder)
      } else {
        msgTo ! Message("No reindexing necessary")
        Future.successful(())
      }
    }
  }
}
