package actors.cleanup

import actors.LongRunningJob.Cancel
import actors.Ticker.Tick
import actors.cleanup.CleanupRunner.CleanupJob
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Terminated}
import play.api.i18n.Messages
import services.ingest.Cleanup
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt


case class CleanupRunnerManager(
  cleanupJob: CleanupJob,
  init: ActorContext => ActorRef,
)(implicit messages: Messages, ec: ExecutionContext) extends Actor with ActorLogging {
  import CleanupRunner._

  override def receive: Receive = {
    case chan: ActorRef =>
      log.debug("Received initial subscriber, starting...")
      val runner = init(context)
      context.become(running(runner, Set(chan)))
      runner ! cleanupJob
      msg(Messages("cleanup.starting", cleanupJob.jobId), Set(chan))
  }

  def running(runner: ActorRef, subs: Set[ActorRef]): Receive = {
    // Add a new message subscriber
    case chan: ActorRef =>
      log.debug(s"Added new message subscriber, ${subs.size}")
      context.watch(chan)
      context.become(running(runner, subs + chan))

    // If runner has died wait a few seconds before terminating ourselves...
    case Terminated(actor) if actor == runner =>
      log.debug(s"Actor terminated: $actor")
      context.system.scheduler.scheduleOnce(5.seconds, self,
        "Cleanup runner unexpectedly shut down")

    // Remove terminated subscribers
    case Terminated(chan) =>
      log.debug(s"Removing subscriber: $chan")
      context.unwatch(chan)
      context.become(running(runner, subs - chan))

    case cleanup: Cleanup =>
      msg(Messages("cleanup.toMove", cleanup.redirects.size), subs)
      msg(Messages("cleanup.toDelete", cleanup.deletions.size), subs)

    case Relinked(_, status) =>
      msg(Messages("cleanup.relinked", status.relinkCount), subs)

    case Redirected(_, status) =>
      msg(Messages("cleanup.redirected", status.redirectCount), subs)

    case Status(_, _, deleteCount) =>
      msg(Messages("cleanup.deleted", deleteCount), subs)

    case Done(d) =>
      log.info(s"Cleanup time: ${d.toSeconds} seconds")
      msg(Messages("cleanup.done"), subs)
      msg(WebsocketConstants.DONE_MESSAGE, subs)
      context.stop(self)

    case Tick(s) =>
      msg(s, subs)

    case Cancel =>
      msg(Messages("cleanup.cancelled"), subs)
      context.stop(self)

    case m =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: Unexpected message: $m", subs)
      context.stop(self)
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
