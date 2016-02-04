package backend.feedback

import backend.FeedbackDAO
import models.Feedback

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}

class MockFeedbackDAO(buffer: collection.mutable.HashMap[Int, Feedback]) extends FeedbackDAO {

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
