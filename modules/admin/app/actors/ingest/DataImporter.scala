package actors.ingest

import actors.Ticker
import actors.ingest.DataImporter._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import models._
import services.ingest.IngestService.{IngestData, IngestJob}
import services.ingest._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}


object DataImporter {
  sealed trait State
  case object Start extends State
  case object ImportBatch extends State
  case class SaveLog(log: ImportLog) extends State
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
        .recover { case e =>
          e.printStackTrace()
          msgTo ! UnexpectedError(e)
        }
        .onComplete { _ => ticker ! Ticker.Stop }

    case sync: SyncLog =>
      msgTo ! Message(s"Sync: moved: ${sync.moved.size}, " +
        s"new: ${sync.created.size}, " +
        s"deleted: ${sync.deleted.size}")

      if (data.params.commit) {
        msgTo ! Message("Creating redirects...")
        ingestApi
          .remapMovedUnits(sync.moved.toSeq)
          .flatMap(done => {
            ingestApi.clearIndex(sync.deleted ++ sync.moved.keys.toSeq, forwarder).map { _ =>
              msgTo ! Message(s"Remapped $done item(s)")
              ingestApi.emitEvents(sync)
              sync.log
            }
          })
          .pipeTo(self)
      }
      else self ! sync.log

    case log: ImportLog =>
      msgTo ! log
      msgTo ! Message(s"Data: created: ${log.created}, " +
        s"updated: ${log.updated}, " +
        s"unchanged: ${log.unchanged}, " +
        s"errors: ${log.errors.size}")
      log.event.foreach(e => msgTo ! Message(s"Event ID: $e"))

      if (data.params.commit) {
        if (log.hasDoneWork) {
          val updated: Seq[String] = data.params.scope +: (
            log.createdKeys.values.flatten.toSeq ++ log.updatedKeys.values.flatten.toSeq)
          msgTo ! Message("Reindexing...")
          ingestApi.emitEvents(log)
          ingestApi.reindex(updated, forwarder).map(_ => self ! SaveLog(log))
        } else {
          msgTo ! Message("No reindexing necessary")
          self ! SaveLog(log)
        }
      } else {
        msgTo ! Message("Task was a dry run so not proceeding to reindex")
        self ! SaveLog(log)
      }

    case SaveLog(log) =>
      msgTo ! Message("Uploading log...")

      ingestApi.storeManifestAndLog(job.jobId, data, log).flatMap { url =>
        val finish = onDone(data, log)
        finish.map { _ =>
          msgTo ! Message(s"Log stored at $url")

          if (todo.isEmpty) {
            msgTo ! Done(start)
          } else {
            context.become(running(msgTo, todo.head, todo.tail, forwarder, start))
            self ! ImportBatch
          }
        }
      }.recover { case e =>
        e.printStackTrace()
        msgTo ! UnexpectedError(e)
      }

    case e: ErrorLog =>
      msgTo ! e

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }
}
