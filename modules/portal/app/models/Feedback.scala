package models

import play.api.libs.json.{Format, Json}
import play.api.data.Form
import play.api.data.Forms._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Feedback(name: Option[String], email: Option[String], text: String, context: Option[FeedbackContext])

object Feedback {
  implicit val format: Format[Feedback] = Json.format[Feedback]

  implicit val form = Form(
    mapping(
      "name" -> optional(text),
      "email" -> optional(email),
      "text" -> nonEmptyText,
      "context" -> ignored(Option.empty[FeedbackContext])
    )(Feedback.apply _)(Feedback.unapply _)
  )
}


