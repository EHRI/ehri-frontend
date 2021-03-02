package actors.harvesting

import java.time.Instant
import java.time.format.DateTimeFormatter
import actors.harvesting.OaiPmhHarvesterManager.{Finalise, OaiPmhHarvestJob}
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import models.{OaiPmhConfig, UserProfile}
import play.api.i18n.Messages
import services.harvesting.{HarvestEventHandle, HarvestEventService, OaiPmhClient, OaiPmhError}
import services.storage.FileStorage
import utils.WebsocketConstants

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


object OaiPmhHarvesterManager {

  /**
    * A description of an OAI-PMH harvest task.
    *
    * @param config     the endpoint configuration
    * @param from       the starting date and time
    * @param prefix     the path prefix on which to save files, after
    *                   which the item identifier will be appended
    */
  case class OaiPmhHarvestData(
    config: OaiPmhConfig,
    prefix: String,
    from: Option[Instant] = None,
  )

  /**
    * A single harvest job with a unique ID.
    */
  case class OaiPmhHarvestJob(repoId: String, datasetId: String, jobId: String, data: OaiPmhHarvestData)

  protected case class Finalise(s: String)
}

case class OaiPmhHarvesterManager(job: OaiPmhHarvestJob, client: OaiPmhClient, storage: FileStorage, eventLog: HarvestEventService)(
  implicit userOpt: Option[UserProfile], messages: Messages, ec: ExecutionContext) extends Actor with ActorLogging {

  import OaiPmhHarvester._
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
      val runner = context.actorOf(Props(OaiPmhHarvester(job, client, storage)))
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
      job.data.from.fold(msg(Messages("harvesting.earliestDate"), subs)) { from =>
        msg(Messages("harvesting.fromDate", DateTimeFormatter.ISO_INSTANT.format(from)), subs)
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
      runAndThen(handle, _.cancel())(Finalise(
        Messages(
          "harvesting.cancelled",
          WebsocketConstants.ERR_MESSAGE,
          count,
          secs
        ))).pipeTo(self)

    // The runner has completed, so we log the
    // event and shut down too
    case Completed(count, secs) =>
      runAndThen(handle, _.close())(Finalise(
        Messages(
          "harvesting.completed",
          WebsocketConstants.DONE_MESSAGE,
          count,
          secs
        ))).pipeTo(self)

    // Error case where the `set` or `from` parameters mean that
    // no records are returned
    case OaiPmhError("noRecordsMatch", _) =>
      self ! Finalise(Messages("harvesting.nothingToDo", WebsocketConstants.DONE_MESSAGE))

    // Error case where we get some other problem...
    case e: OaiPmhError =>
      runAndThen(handle, _.error(e)) (
          Finalise(s"${WebsocketConstants.ERR_MESSAGE}: ${e.code}"))
        .pipeTo(self)

    // The runner has thrown an unexpected error. Log the event
    // and shut down
    case Error(e) =>
      runAndThen(handle, _.error(e))(Finalise(
        Messages(
          "harvesting.error",
          WebsocketConstants.ERR_MESSAGE,
          e.getMessage
        ))).pipeTo(self)

    case Finalise(s) =>
      msg(s, subs)
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def runAndThen(op: Option[HarvestEventHandle], f: HarvestEventHandle => Future[Unit])(
      andThen: => Finalise): Future[Finalise] =
    op.fold(ifEmpty = immediate(()))(s => f(s)).map(_ => andThen)

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
