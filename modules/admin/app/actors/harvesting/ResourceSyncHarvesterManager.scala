package actors.harvesting

import actors.harvesting.ResourceSyncHarvesterManager.ResourceSyncJob
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import models.{ResourceSyncConfig, UserProfile}
import play.api.libs.ws.WSClient
import services.harvesting.{HarvestEventHandle, HarvestEventService, ResourceSyncClient}
import services.storage.FileStorage
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext


object ResourceSyncHarvesterManager {

  /**
    * A description of an OAI-ResourceSync harvest task.
    *
    * @param config     the endpoint configuration
    * @param classifier the storage classifier on which to save files
    * @param prefix     the path prefix on which to save files, after
    *                   which the item identifier will be appended
    */
  case class ResourceSyncData(
    config: ResourceSyncConfig,
    classifier: String,
    prefix: String,
  )

  /**
    * A single harvest job with a unique ID.
    */
  case class ResourceSyncJob(repoId: String, datasetId: String, jobId: String, data: ResourceSyncData)

}


case class ResourceSyncHarvesterManager(job: ResourceSyncJob, ws: WSClient, client: ResourceSyncClient, storage: FileStorage, eventLog: HarvestEventService)(
  implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {

  import ResourceSyncHarvester._
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
      val runner = context.actorOf(Props(ResourceSyncHarvester(job, ws, client, storage)))
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

    // Cancel harvest.. here we tell the runner to exit
    // and shut down on its termination signal...
    case Cancel => runner ! Cancel

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

    // The runner has thrown an unexpected error. Log the event
    // and shut down
    case Error(e) =>
      msg(s"${WebsocketConstants.ERR_MESSAGE}: sync error: ${e.getMessage}", subs)
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