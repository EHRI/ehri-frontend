package actors.ingest

import java.time.LocalDateTime
import actors.ingest.DataImporter._
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.pattern._
import models.{ErrorLog, ImportLog, IngestResult, SyncLog, UserProfile}
import services.ingest.IngestService.IngestJob
import services.ingest._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


// Actor that just prints out a progress indicator
object Ticker {
  case object Run
  case class Tick(tock: String)
  case object Stop
}

case class Ticker()(implicit ec: ExecutionContext) extends Actor {
  private val states = Vector("|", "/", "-", "\\")

  override def receive: Receive = init

  def init: Receive = {
    case (actorRef: ActorRef, msg: String) =>
      val cancellable = context.system.scheduler
        .scheduleAtFixedRate(500.millis, 1000.millis, self, Ticker.Run)
      context.become(tick(actorRef, msg, 0, cancellable))
  }

  def tick(actorRef: ActorRef, msg: String, state: Int, cancellable: Cancellable): Receive = {
    case Ticker.Run =>
      actorRef ! Message(s"$msg... ${states(state)}")
      context.become(tick(actorRef, msg, if (state < 3) state + 1 else 0, cancellable))

    case Ticker.Stop =>
      cancellable.cancel()
  }
}

object DataImporter {
  sealed trait State
  case object Initial extends State
  case class SaveLog(log: ImportLog) extends State
  case class Done(start: LocalDateTime) extends State
  case class Message(msg: String) extends State
  case class UnexpectedError(throwable: Throwable) extends State
}

case class DataImporter(job: IngestJob, ingestApi: IngestService, onDone: (IngestJob, ImportLog) => Future[Unit])(
  implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {

  case class Forwarder(to: ActorRef) extends Actor {
    override def receive: Receive = {
      case s: String => to ! Message(s)
    }
  }

  override def receive: Receive = {
    case Initial =>
      val msgTo = sender()

      // Start a ticker to show progress during the import...
      // This just sends a heartbeat whilst the import future
      // is running.
      val ticker: ActorRef = context.actorOf(Props(Ticker()))
      ticker ! (msgTo -> "Ingesting")

      // Importer initializing...
      context.become(running(msgTo, LocalDateTime.now()))
      msgTo ! Message(s"Initialising ingest for job: ${job.id}...")
      ingestApi
        .importData(job)
        .pipeTo(self)
        .recover { case e =>
            e.printStackTrace()
            msgTo ! UnexpectedError(e)
        }
        .onComplete { _ => ticker ! Ticker.Stop }
  }

  def running(msgTo: ActorRef, start: LocalDateTime): Receive = {
    case result: IngestResult =>
      val forwarder = context.actorOf(Props(Forwarder(msgTo)))
      context.become(finalise(msgTo, forwarder, start))
      self ! result
  }

  def finalise(msgTo: ActorRef, forwarder: ActorRef, start: LocalDateTime): Receive = {
    case sync: SyncLog =>
      msgTo ! Message(s"Sync: moved: ${sync.moved.size}, " +
        s"new: ${sync.created.size}, " +
        s"deleted: ${sync.deleted.size}")

      if (job.data.params.commit) {
        msgTo ! Message("Creating redirects...")
        ingestApi
          .remapMovedUnits(sync.moved.toSeq)
          .flatMap(done => {
            ingestApi.clearIndex(sync.deleted ++ sync.moved.keys.toSeq, forwarder).map { _ =>
              msgTo ! Message(s"Remapped $done item(s)")
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

      if (job.data.params.commit) {
        if (log.hasDoneWork) {
          val updated: Seq[String] = job.data.params.scope +: (
            log.createdKeys.values.flatten.toSeq ++ log.updatedKeys.values.flatten.toSeq)
          msgTo ! Message("Reindexing...")
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

      ingestApi.storeManifestAndLog(job, log).flatMap { url =>
        val finish = onDone(job, log)
        finish.map { _ =>
          msgTo ! Message(s"Log stored at $url")
          msgTo ! Done(start)
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
