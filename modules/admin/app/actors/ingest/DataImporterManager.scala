package actors.ingest

import actors.Ticker.Tick
import actors.ingest.DataImporter.{Done, Start, Message, UnexpectedError}
import org.apache.pekko.actor.SupervisorStrategy.Stop
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import models.{ErrorLog, ImportLog}
import services.ingest.IngestService.{IngestData, IngestJob}
import services.ingest._
import utils.WebsocketConstants

import scala.concurrent.{ExecutionContext, Future}


case class DataImporterManager(
  job: IngestJob,
  ingestApi: IngestService,
  onDone: (IngestData, ImportLog) => Future[Unit] = (_, _) => Future.successful(())
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e =>
      self ! UnexpectedError(e)
      Stop
  }
  // Ready state: we've received a job but won't actually start
  // until there is a channel to talk through
  override def receive: Receive = {
    case chan: ActorRef =>
      log.debug("Received initial subscriber, starting...")
      val runner = context.actorOf(Props(DataImporter(job, ingestApi, onDone)))
      context.become(running(runner, Set(chan)))
      runner ! Start
  }

  def running(runner: ActorRef, subs: Set[ActorRef]): Receive = {

    // The runner has died or been killed...
    case Terminated(actor) if actor == runner =>
      context.stop(self)

    // Add a new message subscriber
    case chan: ActorRef =>
      log.debug(s"Added new message subscriber, ${subs.size}")
      context.watch(chan)
      context.become(running(runner, subs + chan))

    // Remove terminated subscribers
    case Terminated(chan) =>
      log.debug(s"Removing subscriber: $chan")
      context.unwatch(chan)
      context.become(running(runner, subs - chan))

    case Message(m) =>
      msg(m, subs)

    case Tick(s) =>
      msg(s, subs)

    case Done(_) =>
      msg(s"${WebsocketConstants.DONE_MESSAGE}", subs)
      context.stop(self)

    case UnexpectedError(e) =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: ${e.getMessage}", subs)
      context.stop(self)

    // Import error...
    case error: ErrorLog =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: ${error.error}: ${error.details}", subs)
      context.stop(self)
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
