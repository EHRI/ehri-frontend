package backend.parse

import models.Feedback
import backend.FeedbackDAO

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class ParseFeedbackDAO() extends ParseDAO[Feedback]("feedback") with FeedbackDAO {
  val app = play.api.Play.current
}
