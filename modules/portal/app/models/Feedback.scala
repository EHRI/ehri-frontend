package models

import play.api.libs.json.{Format, Json}
import play.api.data.Form
import play.api.data.Forms._
import play.api.Mode.Mode
import org.joda.time.DateTime

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Feedback(
  objectId: Option[String] = None,
  name: Option[String],
  email: Option[String],
  text: String,
  context: Option[FeedbackContext],
  createdAt: Option[DateTime] = None,
  updatedAt: Option[DateTime] = None,
  mode: Option[Mode] = Some(play.api.Mode.Dev)
)

object Feedback {
  implicit val modeFormat = defines.EnumUtils.enumFormat(play.api.Mode)
  implicit val format: Format[Feedback] = Json.format[Feedback]

  implicit val form = Form(
    mapping(
      "objectId" -> ignored(Option.empty[String]),
      "name" -> optional(text),
      "email" -> optional(email),
      "text" -> nonEmptyText,
      "context" -> ignored(Option.empty[FeedbackContext]),
      "createdAt" -> ignored(Option.empty[DateTime]),
      "updatedAt" -> ignored(Option.empty[DateTime]),
      "mode" -> ignored(Option.apply(play.api.Play.current.mode))
    )(Feedback.apply _)(Feedback.unapply _)
  )
}


