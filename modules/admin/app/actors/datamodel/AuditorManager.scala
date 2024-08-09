package actors.datamodel

import actors.LongRunningJob.Cancel
import actors.datamodel.Auditor.{Cancelled, CheckBatch, Checked, Completed, RunAudit}
import actors.datamodel.AuditorManager.{AuditorJob, ItemResult}
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import models._
import play.api.Configuration
import play.api.i18n.Messages
import play.api.libs.json.{Format, Json}
import services.search.{SearchEngine, SearchItemResolver}
import utils.WebsocketConstants

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object AuditorManager {

  case class AuditTask(entityType: EntityType.Value, idPrefix: Option[String] = None, mandatoryOnly: Boolean)
  object AuditTask {
    implicit val _format: Format[AuditTask] = Json.format[AuditTask]
  }

  case class AuditorJob(jobId: String, task: AuditTask)

  case class ItemResult(id: String, mandatory: Seq[String], desirable: Seq[String])
  object ItemResult {
    implicit val _format: Format[ItemResult] = Json.format[ItemResult]
  }
}

case class AuditorManager(job: AuditorJob, searchEngine: SearchEngine, searchItemResolver: SearchItemResolver, fieldMetadataSet: FieldMetadataSet)(
  implicit userOpt: Option[UserProfile], messages: Messages, ec: ExecutionContext, config: Configuration) extends Actor with ActorLogging {

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e =>
      self ! e
      Stop
  }

  // Ready state: we've received a job but won't actually start
  // until there is a channel to talk through
  override def receive: Receive = {
    case chan: ActorRef =>
      log.debug("Received initial subscriber, starting...")
      val maxResults = config.get[Int]("ehri.admin.auditor.maxResults")
      val batchSize = config.get[Int]("ehri.admin.auditor.batchSize")
      val runner = context.actorOf(Props(Auditor(searchEngine, searchItemResolver, fieldMetadataSet, batchSize, maxResults)))
      context.become(running(runner, Set(chan)))
      runner ! RunAudit(job, None)
  }

  /**
    * Running state.
    *
    * @param runner the harvest runner actor
    * @param subs   a set of subscribers to message w/ updates
    */
  def running(runner: ActorRef, subs: Set[ActorRef]): Receive = {

    // Add a new message subscriber
    case chan: ActorRef =>
      log.debug(s"Added new message subscriber, ${subs.size}")
      context.watch(chan)
      context.become(running(runner, subs + chan))

    case Terminated(actor) if actor == runner =>
      log.debug(s"Actor terminated: $actor")
      context.system.scheduler.scheduleOnce(5.seconds, self,
        "Convert runner unexpectedly shut down")

    // Remove terminated subscribers
    case Terminated(chan) =>
      log.debug(s"Removing subscriber: $chan")
      context.unwatch(chan)
      context.become(running(runner, subs - chan))

    // A file has been converted
    case CheckBatch(checks, _) =>
      val res: Seq[ItemResult] = checks.filter(_.errors.nonEmpty).map { check =>
        val mandatory = check.errors.collect { case e: MissingMandatoryField => e }
        val desirable = check.errors.collect { case e: MissingDesirableField => e }
        ItemResult(check.id, mandatory.map(_.id), desirable.map(_.id))
      }
      msg(Json.stringify(Json.toJson(res)), subs)

    // Received confirmation that the runner has shut down
    case Cancelled(checked, _, secs) =>
      msg(Messages("dataModel.audit.cancelled", WebsocketConstants.DONE_MESSAGE, checked, secs), subs)
      context.stop(self)

    // The runner has completed, so we log the
    // event and shut down too
    case Completed(checked, flagged, secs) =>
      msg(Messages("dataModel.audit.completed", WebsocketConstants.DONE_MESSAGE, checked, flagged, secs), subs)
      context.stop(self)

    case Checked(checked) =>
      msg(Messages("dataModel.audit.checked", WebsocketConstants.INFO_MESSAGE,  checked), subs)

    // Cancel conversion... here we tell the runner to exit
    // and shut down on its termination signal...
    case Cancel =>
      log.info("Cancelling audit...")
      runner ! Cancel

    case m =>
      log.error(s"Unexpected message: $m")
  }

  private def msg(s: String, subs: Set[ActorRef]): Unit = {
//    log.info(s + s" (subscribers: ${subs.size})")
    subs.foreach(_ ! s)
  }
}
