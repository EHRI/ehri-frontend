package models

import java.time.ZonedDateTime

import play.api.libs.json.{Format, Json, Reads}
import play.api.data.Form
import play.api.data.Forms._
import play.api.Mode.Mode
import defines.BindableEnum
import defines.EnumUtils.enumMapping

case class Feedback(
  objectId: Option[String] = None,
  userId: Option[String] = None,
  name: Option[String] = None,
  email: Option[String] = None,
  text: Option[String],
  `type`: Option[Feedback.Type.Value] = Some(Feedback.Type.Site),
  copyMe: Option[Boolean] = Some(false),
  context: Option[FeedbackContext] = None,
  createdAt: Option[ZonedDateTime] = None,
  updatedAt: Option[ZonedDateTime] = None,
  mode: Option[Mode] = Some(play.api.Mode.Dev)
)

object Feedback {

  val TEXT = "text"
  val TYPE = "type"
  val ID = "userId"
  val NAME = "name"
  val EMAIL = "email"
  val COPY_ME = "copyMe"

  object Type extends BindableEnum {
    val Site = Value("site")
    val Data = Value("data")

    implicit val _format = defines.EnumUtils.enumFormat(this)
  }

  implicit val modeFormat = defines.EnumUtils.enumFormat(play.api.Mode)
  implicit val _format: Format[Feedback] = Json.format[Feedback]

  implicit val form = Form(
    mapping(
      "objectId" -> ignored(Option.empty[String]),
      ID -> optional(text),
      NAME -> optional(text),
      EMAIL -> optional(email),
      TEXT -> optional(nonEmptyText(maxLength = 16000)),
      TYPE -> optional(enumMapping(Type)),
      COPY_ME -> optional(boolean),
      "context" -> ignored(Option.empty[FeedbackContext]),
      "createdAt" -> ignored(Option.empty[ZonedDateTime]),
      "updatedAt" -> ignored(Option.empty[ZonedDateTime]),
      "mode" -> ignored(Option.empty[play.api.Mode.Value])
    )(Feedback.apply)(Feedback.unapply)
  )
}


