package actors.harvesting

import actors.LongRunningJob.Cancel
import actors.harvesting.Harvester.HarvestJob
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef}
import models.{OaiPmhConfig, UserProfile}
import services.harvesting.{OaiPmhClient, OaiPmhError}
import services.storage.FileStorage

import java.time.{Duration, Instant, LocalDateTime}
import scala.concurrent.ExecutionContext


object OaiPmhHarvester {

  // Possible states for resumption tokens:
  sealed trait ResumptionState
  case class Next(token: String) extends ResumptionState
  case object Empty extends ResumptionState
  object ResumptionState {
    def apply(opt: Option[String]): ResumptionState = opt match {
      case Some(t) => Next(t)
      case _ => Empty
    }
  }

  // Private messages we send ourself
  sealed trait OaiPmhAction
  case class Fetch(ids: List[String], next: ResumptionState, count: Int) extends OaiPmhAction
  case class Resuming(token: String) extends OaiPmhAction

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
    extends HarvestJob
}

case class OaiPmhHarvester (client: OaiPmhClient, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {
  import Harvester._
  import OaiPmhHarvester._
  import akka.pattern.pipe

  override def postStop(): Unit = {
    log.debug("Harvester shut down")
    super.postStop()
  }

  override def receive: Receive = {
    // Start the initial harvest
    case job: OaiPmhHarvestJob =>
      val msgTo = sender()
      context.become(running(job, msgTo, 0, LocalDateTime.now()))
      msgTo ! Starting
      client.listIdentifiers(job.data.config, from = job.data.from)
        .map { case (idents, next) =>
          Fetch(nonDeleted(idents), ResumptionState(next), 0)
        }
        .pipeTo(self)
  }


  // The harvest is running
  def running(job: OaiPmhHarvestJob, msgTo: ActorRef, done: Int, start: LocalDateTime): Receive = {

    // Harvest a new batch via a resumptionToken
    case Next(token) =>
      msgTo ! Resuming(token)
      client.listIdentifiers(job.data.config, resume = Some(token))
        .map { case (idents, next) => Fetch(nonDeleted(idents), ResumptionState(next), done)}
        .pipeTo(self)

    // Harvest an individual item
    case Fetch(id :: rest, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(job, msgTo, count, start))
      val byteSrc = client.getRecord(job.data.config, id)
      storage.putBytes(
        fileName(job.data.prefix, id),
        byteSrc,
        Some("text/xml"),
        meta = Map(
          "source" -> "oaipmh",
          "oaipmh-endpoint" -> job.data.config.url,
          "oaipmh-set" -> job.data.config.set.getOrElse(""),
          "oaipmh-from" -> job.data.config.from.map(_.toString).getOrElse(""),
          "oaipmh-until" -> job.data.config.from.map(_.toString).getOrElse(""),
          "oaipmh-job-id" -> job.jobId
        )
      )
      .map { _ =>
        msgTo ! DoneFile(id)
        Fetch(rest, next, count + 1)
      }
      .pipeTo(self)

    // Finished a batch, start a new one
    case Fetch(Nil, next, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(job, msgTo, count, start))
      self ! next

    // Finish harvesting
    case Empty =>
      msgTo ! Completed(done, done, time(start))

    // Cancel harvest
    case Cancel =>
      msgTo ! Cancelled(done, done, time(start))

    case Failure(e: OaiPmhError) =>
      msgTo ! e

    case Failure(e) =>
      msgTo ! Error(e)

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }

  private def time(from: LocalDateTime): Long =
    Duration.between(from, LocalDateTime.now()).toMillis / 1000

  private def fileName(prefix: String, id: String): String = prefix + id + ".xml"

  private def nonDeleted(idents: Seq[(String, Boolean)]): List[String] = idents
    .filterNot(_._2).map(_._1).toList
}
