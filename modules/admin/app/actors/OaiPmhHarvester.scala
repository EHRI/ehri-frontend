package actors

import java.time.{Duration, LocalDateTime}

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import models.admin.OaiPmhConfig
import services.harvesting.OaiPmhClient
import services.storage.FileStorage
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext


object OaiPmhHarvester {

  // Possible states for resumption tokens:
  sealed trait ResumptionState
  case object Initial extends ResumptionState
  case class Next(token: String) extends ResumptionState
  case object Empty extends ResumptionState
  object ResumptionState {
    def apply(opt: Option[String]): ResumptionState = opt match {
      case Some(t) => Next(t)
      case _ => Empty
    }
  }

  // Other messages we can handle
  sealed trait Action
  case class Fetch(ids: List[String], next: ResumptionState, count: Int) extends Action
  case object Cancel extends Action

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
  case class OaiPmhHarvestJob(id: String, data: OaiPmhHarvestData)
}

case class OaiPmhHarvester (client: OaiPmhClient, storage: FileStorage)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  import OaiPmhHarvester._
  import akka.pattern.pipe

  override def receive: Receive = waiting

  // Waiting to receive a job
  def waiting: Receive = {
    case job: OaiPmhHarvestJob => context.become(ready(job))
  }

  // Ready state: we've received a job but won't actually start
  // until there is a channel to talk through
  def ready(job: OaiPmhHarvestJob): Receive = {
    case chan: ActorRef =>
      context.become(running(job, 0, LocalDateTime.now(), Set(chan)))
      self ! Initial
  }

  // The harvest is running
  def running(job: OaiPmhHarvestJob, done: Int, start: LocalDateTime, subs: Set[ActorRef]): Receive = {

    // Add a new message subscriber
    case chan: ActorRef =>
      log.debug(s"Added new message subscriber, ${subs.size}")
      context.watch(chan)
      context.become(running(job, done, start, subs + chan))

    // Remove terminated subscribers
    case Terminated(chan) =>
      log.debug(s"Removing subscriber: $chan")
      context.unwatch(chan)
      context.become(running(job, done, start, subs - chan))

    // Start the initial harvest
    case Initial =>
      msg(s"Starting harvest with job id: ${job.id}", subs)
      client.listIdentifiers(job.data.config, None)
        .map { case (idents, next) => Fetch(nonDeleted(idents), ResumptionState(next), done)}
        .pipeTo(self)

    // Harvest a new batch via a resumptionToken
    case Next(token) =>
      msg(s"Resuming with $token", subs)
      client.listIdentifiers(job.data.config, Some(token))
        .map { case (idents, next) => Fetch(nonDeleted(idents), ResumptionState(next), done)}
        .pipeTo(self)

    // Harvest an individual item
    case Fetch(id :: rest, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(job, count, start, subs))
      storage.putBytes(
        job.data.classifier,
        fileName(job.data.prefix, id),
        client.getRecord(job.data.config, id),
        Some("text/xml")
      ).map { _ =>
        msg(s"$id", subs)
        Fetch(rest, next, count + 1)
      }.pipeTo(self)

    // Finished a batch, start a new one
    case Fetch(Nil, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(job, count, start, subs))
      self ! next

    // Finish harvesting
    case Empty =>
      msg(s"${WebsocketConstants.DONE_MESSAGE}: " +
        s"Harvested $done file(s) in ${time(start)} seconds", subs)
      context.stop(self)

    // Cancel harvest
    case Cancel =>
      msg(s"Harvested files: $done", subs)
      msg(s"${WebsocketConstants.ERR_MESSAGE}: cancelled after ${time(start)} seconds", subs)
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }

  private def time(from: LocalDateTime): Long =
    Duration.between(from, LocalDateTime.now()).toMillis / 1000

  private def fileName(prefix: String, id: String): String = prefix + id + ".xml"

  private def nonDeleted(idents: Seq[(String, Boolean)]): List[String] = idents
    .filterNot(_._2).map(_._1).toList
}
