package actors.ingest

import actors.harvesting.OaiPmhHarvester.Error
import actors.ingest.DataImporter.{Done, Initial, Message, UnexpectedError}
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import models.{ErrorLog, ImportLog, UserProfile}
import services.ingest.IngestService.IngestJob
import services.ingest._
import utils.WebsocketConstants

import scala.concurrent.{ExecutionContext, Future}


case class DataImporterManager(job: IngestJob, ingestApi: IngestService,
  onDone: (IngestJob, ImportLog) => Future[Unit] = (_, _) => Future.successful(()))
  (implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e =>
      self ! Error(e)
      Stop
  }
  // Ready state: we've received a job but won't actually start
  // until there is a channel to talk through
  override def receive: Receive = {
    case chan: ActorRef =>
      log.debug("Received initial subscriber, starting...")
      val runner = context.actorOf(Props(DataImporter(job, ingestApi, onDone)))
      context.become(running(runner, Set(chan)))
      runner ! Initial
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

    case Done(start) =>
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
