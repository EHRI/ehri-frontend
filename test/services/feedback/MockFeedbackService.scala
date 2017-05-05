package services.feedback

import models.Feedback
import services.data.ItemNotFound
import utils.{Page, PageParams}

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future

case class MockFeedbackService(buffer: collection.mutable.HashMap[Int, Feedback]) extends FeedbackService {

  def get(id: String): Future[Feedback] =
    immediate(buffer.getOrElse(id.toInt, throw new ItemNotFound(id)))

  def create(feedback: Feedback): Future[String] = {
    val key = buffer.size + 1
    buffer += key -> feedback.copy(objectId = Some(key.toString))
    immediate(key.toString)
  }

  def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty): Future[Page[Feedback]] =
    immediate(Page(items = buffer.values.toSeq))

  def delete(id: String): Future[Boolean] = {
    buffer -= id.toInt
    immediate(true)
  }
}
