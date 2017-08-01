package services.feedback

import models.Feedback
import utils.{Page, PageParams}

import scala.concurrent.Future


trait FeedbackService {
  def create(feedback: Feedback): Future[String]
  def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty): Future[Page[Feedback]]
  def delete(id: String): Future[Boolean]
}
