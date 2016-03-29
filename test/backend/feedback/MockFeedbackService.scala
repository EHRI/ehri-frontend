package backend.feedback

import backend.FeedbackService
import models.Feedback

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}

class MockFeedbackService(buffer: collection.mutable.HashMap[Int, Feedback]) extends FeedbackService {

  def create(feedback: Feedback)(implicit executionContext: ExecutionContext): Future[String] = {
    val key = buffer.size + 1
    buffer += key -> feedback.copy(objectId = Some(key.toString))
    immediate(key.toString)
  }
  def list(params: (String,String)*)(implicit executionContext: ExecutionContext) = immediate(buffer.values.toSeq)
  def delete(id: String)(implicit executionContext: ExecutionContext) = {
    buffer -= id.toInt
    immediate(true)
  }
}
