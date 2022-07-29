package actors.harvesting

import actors.LongRunningJob.Cancel
import actors.harvesting.Harvester.HarvestJob
import akka.actor.Status.Failure
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, OneForOneStrategy, SupervisorStrategy, Terminated}
import models.UserProfile
import play.api.i18n.Messages
import services.harvesting.{HarvestEventHandle, HarvestEventService, OaiPmhError}
import utils.WebsocketConstants

import java.time.format.DateTimeFormatter
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}


object HarvesterManager {
  protected case class Finalise(s: String)
}

case class HarvesterManager(job: HarvestJob, init: ActorContext => ActorRef, eventLog: HarvestEventService)(
  implicit userOpt: Option[UserProfile], messages: Messages, ec: ExecutionContext) extends Actor with ActorLogging {

  import Harvester._
  import HarvesterManager._
  import OaiPmhHarvester._
  import akka.pattern.pipe

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e =>
      log.error("Received a supervising error from a child", e)
      self ! Error(e)
      Stop
  }

  override def postStop(): Unit = {
    log.debug("Harvester manager stopped")
    super.postStop()
  }

 // Ready state: we've received a job but won't actually start
  // until there is a channel to talk through
  override def receive: Receive = {
    case chan: ActorRef =>
      log.debug("Received initial subscriber, starting...")
      val runner = init(context)
      context.become(running(runner, Set(chan), Option.empty))
      runner ! job
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

      // If runner has died wait a few seconds before terminating ourselves...
    case Terminated(actor) if actor == runner =>
      log.debug(s"Actor terminated: $actor")
      context.system.scheduler.scheduleOnce(5.seconds, self,
        "Harvest runner unexpectedly shut down")

    // Remove terminated subscribers
    case Terminated(chan) =>
      log.debug(s"Removing subscriber: $chan")
      context.unwatch(chan)
      context.become(running(runner, subs - chan, handle))

    // Confirmation the runner has started
    case Starting =>
      msg(s"Starting harvest with job id: ${job.jobId}", subs)
      job match {
        case OaiPmhHarvestJob(_, _, _, data) =>
          data.config.from.orElse(data.from).fold(ifEmpty = msg(Messages("harvesting.earliestDate"), subs)) { from =>
            msg(Messages("harvesting.fromDate", DateTimeFormatter.ISO_INSTANT.format(from)), subs)
          }
          data.config.until.map { until =>
            msg(Messages("harvesting.untilDate", DateTimeFormatter.ISO_INSTANT.format(until)), subs)
          }
        case _ =>
      }

    case ToDo(num) =>
      msg(Messages("harvesting.syncingFiles", num), subs)

    // Cancel harvest.. here we tell the runner to exit
    // and shut down on its termination signal...
    case Cancel => runner ! Cancel

    // The runner is continuing to harvest via a resumption token
    case Resuming(token) => msg(s"Resuming with $token", subs)

    // A file has been harvested
    case DoneFile(id) =>
      if (handle.isEmpty) {
        log.debug("Initialising event log handle")
        val handle = Await.result(eventLog.save(job.repoId, job.datasetId, job.jobId), 5.seconds)
        context.become(running(runner, subs, Some(handle)))
      }
      msg(id, subs)

    // Received confirmation that the runner has shut down
    case Cancelled(count, _, secs) =>
      runAndThen(handle, _.cancel())(Finalise(
        Messages(
          "harvesting.cancelled",
          WebsocketConstants.ERR_MESSAGE,
          count,
          secs
        ))).pipeTo(self)

    // The runner has completed, so we log the
    // event and shut down too
    case Completed(count, _, secs) =>
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
          Finalise(
            s"${WebsocketConstants.ERR_MESSAGE}: ${e.errorMessage}"))
        .pipeTo(self)

    // The runner has thrown an unexpected error. Log the event
    // and shut down
    case Error(e) =>
      log.debug(s"Finalising with error: ${e.getMessage}")
      runAndThen(handle, _.error(e))(Finalise(
        Messages(
          "harvesting.error",
          WebsocketConstants.ERR_MESSAGE,
          e.getMessage
        ))).pipeTo(self)

    case Finalise(s) =>
      log.debug("Running finalise on harvest manager")
      msg(s, subs)
      context.become(running(runner, subs, Option.empty))
      context.stop(self)

    case Failure(e) =>
      log.error("Harvesting error {}", e)

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def runAndThen(op: Option[HarvestEventHandle], f: HarvestEventHandle => Future[Unit])(
      andThen: => Finalise): Future[Finalise] = {
    op.fold(ifEmpty = immediate(()))(s => f(s)).map(_ => andThen)
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
