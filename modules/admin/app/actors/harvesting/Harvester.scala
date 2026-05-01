package actors.harvesting

import org.apache.pekko.actor.Actor

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import org.apache.pekko.pattern.after

object Harvester {
  sealed trait HarvestAction
  case class Cancelled(done: Int, fresh: Int, secs: Long) extends HarvestAction
  case class Completed(done: Int, fresh: Int, secs: Long) extends HarvestAction
  case class DoneFile(id: String) extends HarvestAction
  case class Error(e: Throwable) extends HarvestAction
  case class ToDo(num: Int) extends HarvestAction
  case object Starting extends HarvestAction

  trait HarvestJob {
    def repoId: String
    def datasetId: String
    def jobId: String
  }
}

trait Harvester { self: Actor =>
  /**
    * Get the duration in seconds between a given instant and now.
    * @param from the previous time
    * @return the number of elapsed seconds
    */
  protected def time(from: Instant): Long =
    Duration.between(from, Instant.now()).toMillis / 1000

  /**
    * Return a result with an optional delay in milliseconds.
    *
    * @param waitMillis a millisecond delay
    * @param res a result
    * @tparam T the type of result
    * @return a potentially-delayed Future of the result.
    */
  protected def delayIf[T](waitMillis: Option[Int], res: T)(implicit ec: ExecutionContext): Future[T] =
    waitMillis.filter(_ > 0).map { wait =>
      after(wait.millis, context.system.scheduler)(Future.successful(res))
    }.getOrElse(Future.successful(res))
}
