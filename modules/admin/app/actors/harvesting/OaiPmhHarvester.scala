package actors.harvesting

import java.time.Instant
import java.time.format.DateTimeFormatter

import actors.harvesting.OaiPmhHarvester.OaiPmhHarvestJob
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import models.{OaiPmhConfig, UserProfile}
import services.harvesting.{HarvestEventHandle, HarvestEventService, OaiPmhClient, OaiPmhError}
import services.storage.FileStorage
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext


object OaiPmhHarvester {

  /**
    * A description of an OAI-PMH harvest task.
    *
    * @param config     the endpoint configuration
    * @param from       the starting date and time
    * @param classifier the storage classifier on which to save files
    * @param prefix     the path prefix on which to save files, after
    *                   which the item identifier will be appended
    */
  case class OaiPmhHarvestData(
    config: OaiPmhConfig,
    classifier: String,
    prefix: String,
    from: Option[Instant] = None,
  )

  /**
    * A single harvest job with a unique ID.
    */
  case class OaiPmhHarvestJob(repoId: String, datasetId: String, jobId: String, data: OaiPmhHarvestData)

}

case class OaiPmhHarvester(job: OaiPmhHarvestJob, client: OaiPmhClient, storage: FileStorage, eventLog: HarvestEventService)(
  implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {

  import OaiPmhHarvestRunner._
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
      log.debug("Received initial subscriber, starting...")
      val runner = context.actorOf(Props(OaiPmhHarvestRunner(job, client, storage)))
      context.become(running(runner, Set(chan), Option.empty))
      runner ! Initial
  }

  /**
    * Running state.
    *
    * @param runner the harvest runner actor
    * @param subs   a set of subscribers to message w/ updates
    * @param handle a handle through which the job logging can be concluded
    */
  def running(runner: ActorRef, subs: Set[ActorRef], handle: Option[HarvestEventHandle]): Receive = {

    // Add a new message subscriber
    case chan: ActorRef =>
      log.debug(s"Added new message subscriber, ${subs.size}")
      context.watch(chan)
      context.become(running(runner, subs + chan, handle))

    case Terminated(actor) if actor == runner =>
      context.stop(self)

    // Remove terminated subscribers
    case Terminated(chan) =>
      log.debug(s"Removing subscriber: $chan")
      context.unwatch(chan)
      context.become(running(runner, subs - chan, handle))

    // Confirmation the runner has started
    case Starting =>
      msg(s"Starting harvest with job id: ${job.jobId}", subs)
      job.data.from.fold(msg("Harvesting from earliest date", subs)) { from =>
        msg(s"Harvesting from ${DateTimeFormatter.ISO_INSTANT.format(from)}", subs)
      }

    // Cancel harvest.. here we tell the runner to exit
    // and shut down on its termination signal...
    case Cancel => runner ! Cancel

    // The runner is continuing to harvest via a resumption token
    case Resuming(token) => msg(s"Resuming with $token", subs)

    // A file has been harvested
    case DoneFile(id) =>
      msg(id, subs)
      if (handle.isEmpty) {
        eventLog.save(job.repoId, job.datasetId, job.jobId).pipeTo(self)
      }

      // We've received a log handle which we can use to say how
      // this job finished: via error, cancellation, or otherwise
    case handle: HarvestEventHandle =>
      context.become(running(runner, subs, Some(handle)))

    // Received confirmation that the runner has shut down
    case Cancelled(count, secs) =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: cancelled after $count file(s) in $secs seconds", subs)
      handle.foreach(_.cancel())
      context.stop(self)

    // The runner has completed, so we log the
    // event and shut down too
    case Completed(count, secs) =>
      msg(s"${WebsocketConstants.DONE_MESSAGE}: " +
        s"harvested $count file(s) in $secs seconds", subs)
      handle.foreach(_.close())
      context.stop(self)

    // Error case where the `set` or `from` parameters mean that
    // no records are returned
    case OaiPmhError("noRecordsMatch", _) =>
      msg(s"${WebsocketConstants.DONE_MESSAGE}: nothing to harvest", subs)
      context.stop(self)

    // Error case where we get some other problem...
    case e: OaiPmhError =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: ${e.code}", subs)
      handle.map(_.error(e))
      context.stop(self)

    // The runner has thrown an unexpected error. Log the event
    // and shut down
    case Error(e) =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: harvesting error: ${e.getMessage}", subs)
      handle.foreach(_.error(e))
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
