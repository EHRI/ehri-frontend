package mocks

import backend.FeedbackDAO
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}
import models.Feedback

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class MockFeedbackDAO extends FeedbackDAO {
  def create(feedback: Feedback)(implicit executionContext: ExecutionContext): Future[String] = immediate("test-id")
  def list()(implicit executionContext: ExecutionContext) = immediate(Seq(Feedback(Some("body"), None, "Hello, world", None)))
  def delete(id: String)(implicit executionContext: ExecutionContext) = immediate(true)
}
