package backend.feedback

import backend.FeedbackService
import models.Feedback
import utils.{Page, PageParams}

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}

class MockFeedbackService(buffer: collection.mutable.HashMap[Int, Feedback]) extends FeedbackService {

  def create(feedback: Feedback)(implicit executionContext: ExecutionContext): Future[String] = {
    val key = buffer.size + 1
    buffer += key -> feedback.copy(objectId = Some(key.toString))
    immediate(key.toString)
  }
  def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty)(implicit executionContext: ExecutionContext) =
    immediate(Page(items = buffer.values.toSeq))
  def delete(id: String)(implicit executionContext: ExecutionContext) = {
    buffer -= id.toInt
    immediate(true)
  }
}
