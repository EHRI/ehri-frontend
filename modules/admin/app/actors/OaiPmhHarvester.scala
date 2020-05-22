package actors

import java.time.{Duration, LocalDateTime}

import akka.actor.{Actor, ActorRef}
import models.admin.OaiPmhConfig
import services.harvesting.OaiPmhClient
import services.storage.FileStorage
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext


object OaiPmhHarvester {
  sealed trait TokenState
  case object Initial extends TokenState
  case class Next(token: String) extends TokenState
  case class Fetch(ids: List[String], next: TokenState) extends TokenState
  case object Empty extends TokenState
  case object Cancel extends TokenState
  object TokenState {
    def apply(opt: Option[String]): TokenState = opt match {
      case Some(t) => Next(t)
      case _ => Empty
    }
  }

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

case class OaiPmhHarvester (client: OaiPmhClient, storage: FileStorage)(implicit exec: ExecutionContext) extends Actor {

  import OaiPmhHarvester._

  override def receive: Receive = waiting

  // Waiting to receive a job
  def waiting: Receive = {
    case job: OaiPmhHarvestJob => context.become(ready(job))
  }

  // Ready state: we've received a job but won't actually start
  // until there is a channel to talk through
  def ready(job: OaiPmhHarvestJob): Receive = {
    case chan: ActorRef =>
      context.become(running(job, 0, LocalDateTime.now(), Seq(chan)))
      self ! Initial
  }

  // The harvest is running
  def running(job: OaiPmhHarvestJob, done: Int, start: LocalDateTime, chan: Seq[ActorRef]): Receive = {

    // Add a new subscriber to messages
    case channel: ActorRef => context.become(running(job, done, start, chan :+ channel))

    // Start the initial harvest
    case Initial =>
      msg(s"Starting harvest with job id: ${job.id}", chan)
      client.listIdentifiers(job.data.config, None).map { case (idents, next) =>
        self ! Fetch(nonDeleted(idents), TokenState(next))
      }

    // Resume harvesting
    case Next(token) =>
      msg(s"Resuming with $token", chan)
      client.listIdentifiers(job.data.config, Some(token)).map { case (idents, next) =>
        val ids = idents.filterNot(_._2).map(_._1).toList
        self ! Fetch(nonDeleted(idents), TokenState(next))
      }

    case Fetch(id :: rest, next) =>
      storage.putBytes(
        job.data.classifier,
        fileName(job.data.prefix, id),
        client.getRecord(job.data.config, id),
        Some("text/xml")
      ).map { uri =>
        msg(s"$id", chan)
        context.become(running(job, done + 1, start, chan))
        self ! Fetch(rest, next)
      }

    case Fetch(Nil, next) => self ! next

    // Finish harvesting
    case Empty =>
      msg(s"${WebsocketConstants.DONE_MESSAGE}: " +
        s"Harvested $done file(s) in ${time(start)} seconds", chan)
      context.stop(self)

    // Cancel harvest
    case Cancel =>
      msg(s"Harvested files: $done", chan)
      msg(s"${WebsocketConstants.ERR_MESSAGE}: cancelled after ${time(start)} seconds", chan)
      context.stop(self)
  }

  private def time(from: LocalDateTime): Long =
    Duration.between(from, LocalDateTime.now()).toMillis / 1000

  private def msg(s: String, chan: Seq[ActorRef]): Unit = {
    chan.foreach(_ ! s)
  }

  private def fileName(prefix: String, id: String): String = prefix + id + ".xml"

  private def nonDeleted(idents: Seq[(String, Boolean)]): List[String] = idents
    .filterNot(_._2).map(_._1).toList
}
