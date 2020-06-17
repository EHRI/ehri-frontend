package actors

import java.time.{Duration, LocalDateTime}

import actors.OaiPmhHarvester.OaiPmhHarvestJob
import akka.actor.{Actor, ActorLogging}
import models.UserProfile
import services.harvesting.OaiPmhClient
import services.storage.FileStorage
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext
object OaiPmhHarvestRunner {
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
  case object Completed extends Action
  case object Cancel extends Action
  case class Error(e: Throwable) extends Action
  case class Message(s: String) extends Action
}

case class OaiPmhHarvestRunner (job: OaiPmhHarvestJob, client: OaiPmhClient, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {
  import OaiPmhHarvestRunner._
  import akka.pattern.pipe

  override def receive: Receive = running(0, LocalDateTime.now())

  // The harvest is running
  def running(done: Int, start: LocalDateTime): Receive = {

    // Start the initial harvest
    case Initial =>
      msg(s"Starting harvest with job id: ${job.jobId}")
      client.listIdentifiers(job.data.config, None)
        .map { case (idents, next) => Fetch(nonDeleted(idents), ResumptionState(next), done)}
        .pipeTo(self)

    // Harvest a new batch via a resumptionToken
    case Next(token) =>
      msg(s"Resuming with $token")
      client.listIdentifiers(job.data.config, Some(token))
        .map { case (idents, next) => Fetch(nonDeleted(idents), ResumptionState(next), done)}
        .pipeTo(self)

    // Harvest an individual item
    case Fetch(id :: rest, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(count, start))
      val byteSrc = client.getRecord(job.data.config, id)
      storage.putBytes(
        job.data.classifier,
        fileName(job.data.prefix, id),
        byteSrc,
        Some("text/xml"),
        meta = Map(
          "source" -> "oaipmh",
          "oaipmh-endpoint" -> job.data.config.url,
          "oaipmh-set" -> job.data.config.set.getOrElse("")
        )
      ).map { _ =>
        msg(s"$id")
        Fetch(rest, next, count + 1)
      }.pipeTo(self)

    // Finished a batch, start a new one
    case Fetch(Nil, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(count, start))
      self ! next

    // Finish harvesting
    case Empty =>
      msg(s"${WebsocketConstants.DONE_MESSAGE}: " +
        s"Harvested $done file(s) in ${time(start)} seconds")
      context.stop(self)
      context.parent ! Completed

    // Cancel harvest
    case Cancel =>
      msg(s"Harvested files: $done")
      msg(s"${WebsocketConstants.ERR_MESSAGE}: cancelled after ${time(start)} seconds")
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def msg(s: String): Unit = {
    log.info(s)
    context.parent ! Message(s)
  }

  private def time(from: LocalDateTime): Long =
    Duration.between(from, LocalDateTime.now()).toMillis / 1000

  private def fileName(prefix: String, id: String): String = prefix + id + ".xml"

  private def nonDeleted(idents: Seq[(String, Boolean)]): List[String] = idents
    .filterNot(_._2).map(_._1).toList
}
