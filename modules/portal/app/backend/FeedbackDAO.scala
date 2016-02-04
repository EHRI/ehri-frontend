package backend

import models.Feedback
import scala.concurrent.{ExecutionContext, Future}

trait FeedbackDAO {
  def create(feedback: Feedback)(implicit executionContext: ExecutionContext): Future[String]
  def list(params: (String,String)*)(implicit executionContext: ExecutionContext): Future[Seq[Feedback]]
  def delete(id: String)(implicit executionContext: ExecutionContext): Future[Boolean]
}
