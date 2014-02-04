package mocks

import backend.FeedbackDAO
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}
import models.Feedback
import scala.util.Random

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class MockFeedbackDAO extends FeedbackDAO {

  var buffer = Map.empty[Int,Feedback]

  def create(feedback: Feedback)(implicit executionContext: ExecutionContext): Future[String] = {
    val key = buffer.size + 1
    buffer += key -> feedback.copy(objectId = Some(key.toString))
    immediate(key.toString)
  }
  def list()(implicit executionContext: ExecutionContext) = immediate(buffer.values.toSeq)
  def delete(id: String)(implicit executionContext: ExecutionContext) = {
    buffer -= id.toInt
    immediate(true)
  }
}
