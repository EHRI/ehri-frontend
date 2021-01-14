package actors.harvesting

import java.time.{Duration, LocalDateTime}

import actors.harvesting.OaiPmhHarvesterManager.OaiPmhHarvestJob
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef}
import models.UserProfile
import services.harvesting.OaiPmhClient
import services.storage.FileStorage

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

  // Internal message we send ourselves
  private case class Fetch(ids: List[String], next: ResumptionState, count: Int) extends Action

  // Other messages we can handle
  sealed trait Action
  case object Starting extends Action
  case class Completed(total: Int, secs: Long) extends Action
  case class Error(e: Throwable) extends Action
  case class Resuming(token: String) extends Action
  case class DoneFile(id: String) extends Action
  case class Cancelled(total: Int, secs: Long) extends Action
  case object Cancel extends Action
}

case class OaiPmhHarvester (job: OaiPmhHarvestJob, client: OaiPmhClient, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {
  import OaiPmhHarvester._
  import akka.pattern.pipe

  override def receive: Receive = {
    // Start the initial harvest
    case Initial =>
      val msgTo = sender()
      context.become(running(msgTo, 0, LocalDateTime.now()))
      msgTo ! Starting
      client.listIdentifiers(job.data.config, from = job.data.from)
        .map { case (idents, next) => Fetch(nonDeleted(idents), ResumptionState(next), 0)}
        .pipeTo(self)
  }


  // The harvest is running
  def running(msgTo: ActorRef, done: Int, start: LocalDateTime): Receive = {

    // Harvest a new batch via a resumptionToken
    case Next(token) =>
      msgTo ! Resuming(token)
      client.listIdentifiers(job.data.config, resume = Some(token))
        .map { case (idents, next) => Fetch(nonDeleted(idents), ResumptionState(next), done)}
        .pipeTo(self)

    // Harvest an individual item
    case Fetch(id :: rest, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(msgTo, count, start))
      val byteSrc = client.getRecord(job.data.config, id)
      storage.putBytes(
        fileName(job.data.prefix, id),
        byteSrc,
        Some("text/xml"),
        meta = Map(
          "source" -> "oaipmh",
          "oaipmh-endpoint" -> job.data.config.url,
          "oaipmh-set" -> job.data.config.set.getOrElse(""),
          "oaipmh-job-id" -> job.jobId
        )
      ).map { _ =>
        msgTo ! DoneFile(id)
        Fetch(rest, next, count + 1)
      }.pipeTo(self)

    // Finished a batch, start a new one
    case Fetch(Nil, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(msgTo, count, start))
      self ! next

    // Finish harvesting
    case Empty =>
      context.stop(self)
      msgTo ! Completed(done, time(start))

    // Cancel harvest
    case Cancel =>
      msgTo ! Cancelled(done, time(start))
      context.stop(self)

    case Failure(e) =>
      msgTo ! e
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }

  private def time(from: LocalDateTime): Long =
    Duration.between(from, LocalDateTime.now()).toMillis / 1000

  private def fileName(prefix: String, id: String): String = prefix + id + ".xml"

  private def nonDeleted(idents: Seq[(String, Boolean)]): List[String] = idents
    .filterNot(_._2).map(_._1).toList
}
