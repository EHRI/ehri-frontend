package actors.datamodel

import actors.LongRunningJob.Cancel
import actors.datamodel.AuditorManager.{AuditTask, AuditorJob}
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef}
import org.apache.pekko.pattern._
import models._
import services.data.DataUser
import services.search._
import utils.PageParams

import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext

object Auditor {
  sealed trait Action

  case class RunAudit(job: AuditorJob, query: Option[SearchQuery] = None) extends Action

  case class Check(id: String, errors: Seq[ValidationError])

  case class Checked(checked: Int) extends Action

  case class Completed(checked: Int, count: Int, secs: Long = 0) extends Action

  case class Cancelled(checked: Int, count: Int, secs: Long = 0) extends Action

  case class CheckBatch(checks: Seq[Check], more: Boolean)

  private case class Batch(job: AuditorJob, items: Seq[Entity], query: SearchQuery, more: Boolean) extends Action
}


case class Auditor(searchEngine: SearchEngine, resolver: SearchItemResolver, fieldMetadataSet: FieldMetadataSet, batchSize: Int, maxFlagged: Int)(
    implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {
  import Auditor._
  private implicit val apiUser: DataUser = DataUser(userOpt.map(_.id))

  override def receive: Receive = {
    case e: RunAudit =>
      context.become(running(sender(), Instant.now(), 0, 0))
      self ! e
  }

  def running(msgTo: ActorRef, start: Instant, checked: Int, flagged: Int): Receive = {
    case RunAudit(job, queryOpt) =>
      // Search for entities to audit
      val query: SearchQuery = queryOpt.getOrElse(initSearch(job.task))

      searchEngine.search(query).flatMap { res =>
        resolver.resolve[Entity](res.page.items).map { list =>
          Batch(job, list.flatten, query, res.page.hasMore)
        }
      }.pipeTo(self)

    case Batch(job, items, query, more) =>
      log.debug(s"Found ${items.size} items for audit, total so far: $checked")
      val errors: Seq[Check] = items.flatMap { item =>
        val errs = fieldMetadataSet.validate(item)
        val pErrs = errs.collect { case e@MissingMandatoryField(_) => e}
        if (job.task.mandatoryOnly && pErrs.isEmpty) None
        else if (errs.nonEmpty) Some(Check(item.id, errs))
        else None
      }

      if (checked % batchSize == 0) {
        msgTo ! Checked(checked)
      }

      msgTo ! CheckBatch(errors, more)

      if (more && (flagged + errors.size) < maxFlagged) {
        val next = query.copy(paging = query.paging.next)
        context.become(running(msgTo, start, checked + items.size, flagged + errors.size))
        self ! RunAudit(job, Some(next))
      } else {
        msgTo ! Completed(checked + items.size, flagged + errors.size, time(start))
      }

    case Cancel =>
      log.debug(s"Cancelling audit job, checked so far: $checked, flagged: $flagged")
      sender() ! Cancelled(checked, flagged)
  }

  private def time(from: Instant): Long = Duration.between(from, Instant.now()).toMillis / 1000

  private def initSearch(task: AuditTask): SearchQuery = {
    val paging: PageParams = PageParams(limit = batchSize)
    val params: SearchParams = SearchParams(entities = Seq(task.entityType), query = task.idPrefix.filter(_.nonEmpty).map(p => s"$p*"), sort = Some(SearchSort.Id))
    val facets = if(task.manualOnly) List(FieldFacetClass(key = SearchConstants.CREATION_PROCESS, name = "Creation Process", param = "creation")) else Nil
    val applied = if(task.manualOnly) Seq(AppliedFacet(SearchConstants.CREATION_PROCESS, Seq(Description.CreationProcess.Manual.toString))) else Nil
    SearchQuery(params = params, paging = paging, appliedFacets = applied, user = userOpt, facetClasses = facets)
  }
}
