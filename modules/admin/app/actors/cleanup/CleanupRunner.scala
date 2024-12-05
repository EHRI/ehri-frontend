package actors.cleanup

import actors.Ticker
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Props}
import play.api.Configuration
import services.data.{DataService, EventForwarder}
import services.ingest.{Cleanup, ImportLogService, IngestService}

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}


object CleanupRunner {
  case class Status(relinkCount: Int = 0, redirectCount: Int = 0, deleteCount: Int = 0)

  sealed trait CleanupState

  case class Relinked(cleanup: Cleanup, status: Status) extends CleanupState

  case class Redirected(cleanup: Cleanup, status: Status) extends CleanupState

  case class DeleteBatch(cleanup: Cleanup, todo: Seq[String], status: Status) extends CleanupState

  case class Done(secs: Duration) extends CleanupState


  case class CleanupJob(repoId: String, snapshotId: Int, jobId: String, msg: String)
}

/**
  * Cleanup runner actor. This actor is responsible for running a cleanup job, which
  * consists of three tasks: relinking, which means moving any links or references from
  * the old item to the new one; redirecting, which means creating a redirect from the
  * old item to the new one; and deleting, which means deleting the old items.
  *
  * Deleting is done in batches to avoid overloading the database backend.
  */
case class CleanupRunner(
  dataApi: DataService,
  logService: ImportLogService,
  importService: IngestService,
  eventForwarder: ActorRef
)(implicit config: Configuration, ec: ExecutionContext) extends Actor with ActorLogging {

  import CleanupRunner._
  import org.apache.pekko.pattern._

  private val batchSize = config.get[Int]("ehri.admin.bulkOperations.maxDeletions")

  override def receive: Receive = {
    // Start the initial harvest
    case job: CleanupJob =>
      val msgTo = sender()
      context.become(running(job, msgTo, Status(), Instant.now()))
      logService.cleanup(job.repoId, job.snapshotId)
        .pipeTo(self)
  }

  def running(job: CleanupJob, msgTo: ActorRef, status: Status, time: Instant): Receive = {
    // Launch the relink task
    case cleanup: Cleanup =>
      msgTo ! cleanup
      dataApi.relinkTargets(cleanup.redirects, tolerant = true, commit = true)
        .map(_.map(_._3).sum)
        .map(c => Relinked(cleanup, status.copy(relinkCount = c)))
        .pipeTo(self)

    // Launch the redirect task
    case r@Relinked(cleanup, newStatus) =>
      msgTo ! r
      importService.remapMovedUnits(cleanup.redirects)
        .map { _ => // NB: the API gives us a number that is the total number
          // of redirected URLs, but we want the number of redirected items,
          // so it's a bit of a fake here...
          Redirected(cleanup, newStatus.copy(redirectCount = cleanup.redirects.size))
        }
        .pipeTo(self)

    // Launch the delete task
    case r@Redirected(cleanup, newStatus) =>
      msgTo ! r
      self ! DeleteBatch(cleanup, cleanup.deletions, newStatus)

    // Delete a batch of items
    case DeleteBatch(cleanup, todo, newStatus) if todo.nonEmpty =>
      val ticker: ActorRef = context.actorOf(Props(Ticker()))
      ticker ! (msgTo -> "Deleting...")

      val (batch, rest) = todo.splitAt(batchSize)
      dataApi.batchDelete(batch, Some(job.repoId), logMsg = job.msg,
          version = true, tolerant = true, commit = true)
        .map { batchCount =>
          eventForwarder ! EventForwarder.Delete(batch)
          val st = newStatus.copy(deleteCount = newStatus.deleteCount + batchCount)
          msgTo ! st
          DeleteBatch(cleanup, rest, st)
        }
        .recover { case e =>
          e.printStackTrace()
          msgTo ! e
        }
        .pipeTo(self)
        .onComplete(_ => ticker ! Ticker.Stop)

    // When there are no more items to delete, save the cleanup log and finish
    case DeleteBatch(cleanup, todo, _) if todo.isEmpty =>
      logService.saveCleanup(job.repoId, job.snapshotId, cleanup)
        .map(_ => msgTo ! Done(FiniteDuration(java.time.Duration.between(time, Instant.now).toNanos, TimeUnit.NANOSECONDS)))

    case m =>
      msgTo ! s"Unexpected message: $m"
  }
}
