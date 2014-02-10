package backend

import models.Feedback
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait FeedbackDAO {
  def create(feedback: Feedback)(implicit executionContext: ExecutionContext): Future[String]
  def list()(implicit executionContext: ExecutionContext): Future[Seq[Feedback]]
  def delete(id: String)(implicit executionContext: ExecutionContext): Future[Boolean]
}
