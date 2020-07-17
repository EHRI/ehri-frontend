package actors.transformation

import java.time.Instant

import actors.transformation.XmlConvertRunner._
import actors.transformation.XmlConverter.XmlConvertJob
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import defines.FileStage
import models.{ConvertConfig, UserProfile}
import services.harvesting.HarvestEventHandle
import services.storage.FileStorage
import services.transformation.XmlTransformer
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext


object XmlConverter {
  /**
    * A description of a conversion task.
    *
    * @param config     the conversion configuration
    * @param from       the starting date and time
    * @param classifier the storage classifier on which to save files
    * @param outPrefix     the path prefix on which to save files, after
    *                   which the item identifier will be appended
    */
  case class XmlConvertData(
    config: ConvertConfig,
    classifier: String,
    inPrefix: FileStage.Value => String,
    outPrefix: String,
    from: Option[Instant] = None,
  )

  /**
    * A single convert job with a unique ID.
    */
  case class XmlConvertJob(jobId: String, repoId: String, data: XmlConvertData)


}

case class XmlConverter(job: XmlConvertJob, transformer: XmlTransformer, storage: FileStorage)(
  implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {

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
      val runner = context.actorOf(Props(XmlConvertRunner(job, transformer, storage)))
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
      msg(s"Starting convert with job id: ${job.jobId}", subs)

    // Cancel conversion... here we tell the runner to exit
    // and shut down on its termination signal...
    case Cancel => runner ! Cancel

    // A file has been converted
    case DoneFile(id) =>
      msg(id, subs)

    case Resuming(from) =>
      msg(s"Resuming from marker: $from", subs)

    // Received confirmation that the runner has shut down
    case Cancelled(count, secs) =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: cancelled after $count file(s) in $secs seconds", subs)
      context.stop(self)

    // The runner has completed, so we log the
    // event and shut down too
    case Completed(count, secs) =>
      msg(s"${WebsocketConstants.DONE_MESSAGE}: " +
        s"converted $count file(s) in $secs seconds", subs)
      context.stop(self)

    // The runner has thrown an unexpected error. Log the event
    // and shut down
    case Error(e) =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: conversion error: ${e.getMessage}", subs)
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
