package actors

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime

import actors.OaiPmhHarvestRunner.{Cancel, Completed, Error, Initial, Message}
import actors.OaiPmhHarvester.OaiPmhHarvestJob
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import models.HarvestEvent.HarvestEventType
import models.{OaiPmhConfig, UserProfile}
import services.harvesting.{HarvestEventService, OaiPmhClient}
import services.storage.FileStorage
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext


object OaiPmhHarvester {

  /**
    * A description of an OAI-PMH harvest task.
    *
    * @param config     the endpoint configuration
    * @param from       the starting date and time
    * @param to         the ending date and time
    * @param classifier the storage classifier on which to save files
    * @param prefix     the path prefix on which to save files, after
    *                   which the item identifier will be appended
    */
  case class OaiPmhHarvestData(
    config: OaiPmhConfig,
    classifier: String,
    prefix: String,
    from: Option[LocalDateTime] = None,
    to: Option[LocalDateTime] = None,
  )

  /**
    * A single harvest job with a unique ID.
    */
  case class OaiPmhHarvestJob(jobId: String, repoId: String, data: OaiPmhHarvestData)

}

case class OaiPmhHarvester(job: OaiPmhHarvestJob, client: OaiPmhClient, storage: FileStorage, eventLog: HarvestEventService)(
  implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {

  import akka.pattern.pipe

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e =>
      self ! Error(e)
      Stop
  }

  // Ready state: we've received a job but won't actually start
  // until there is a channel to talk through
  override def receive: Receive = {
    case chan: ActorRef =>
      val runner = context.actorOf(Props(OaiPmhHarvestRunner(job, client, storage)))
      context.become(running(runner, Set(chan)))
      eventLog
        .save(job.repoId, job.jobId, HarvestEventType.Started)
        .map(_ => Initial)
        .pipeTo(runner)
  }

  // The harvest is running
  def running(runner: ActorRef, subs: Set[ActorRef]): Receive = {

    // Add a new message subscriber
    case chan: ActorRef =>
      log.debug(s"Added new message subscriber, ${subs.size}")
      context.watch(chan)
      context.become(running(runner, subs + chan))

    case Terminated(actor) if actor == runner =>
      context.stop(self)

    // Remove terminated subscribers
    case Terminated(chan) =>
      log.debug(s"Removing subscriber: $chan")
      context.unwatch(chan)
      context.become(running(runner, subs - chan))

    // The harvest runner has a message to send to
    // the subscribers
    case Message(s) => msg(s, subs)

    // Cancel harvest.. here we tell the runner to exit
    // and shut down on its termination signal...
    case Cancel =>
      eventLog.save(job.repoId, job.jobId, HarvestEventType.Cancelled)
        .map(_ => Cancel)
        .pipeTo(runner)

    // The runner has completed, so we log the
    // event and shut down too
    case Completed =>
      eventLog.save(job.repoId, job.jobId, HarvestEventType.Completed)
      context.stop(self)

    // The runner has thrown an unexpected error. Log the event
    // and shut down
    case Error(e) =>
      val sw = new StringWriter()
      e.printStackTrace(new PrintWriter(sw))
      eventLog.save(job.repoId, job.jobId, HarvestEventType.Errored, Some(sw.toString))
      msg(s"${WebsocketConstants.ERR_MESSAGE}: harvesting error: ${e.getMessage}", subs)
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
