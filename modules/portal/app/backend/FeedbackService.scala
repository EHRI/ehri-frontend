package backend

import models.Feedback
import utils.{Page, PageParams}
import scala.concurrent.{ExecutionContext, Future}

trait FeedbackService {
  def create(feedback: Feedback)(implicit executionContext: ExecutionContext): Future[String]
  def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty)(
        implicit executionContext: ExecutionContext): Future[Page[Feedback]]
  def delete(id: String)(implicit executionContext: ExecutionContext): Future[Boolean]
}
