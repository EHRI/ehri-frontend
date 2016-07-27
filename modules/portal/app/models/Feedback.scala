package models

import play.api.libs.json.{Reads, Format, Json}
import play.api.data.Form
import play.api.data.Forms._
import play.api.Mode.Mode
import org.joda.time.DateTime
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
  createdAt: Option[DateTime] = None,
  updatedAt: Option[DateTime] = None,
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
  implicit val isoJodaDateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
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
      "createdAt" -> ignored(Option.empty[DateTime]),
      "updatedAt" -> ignored(Option.empty[DateTime]),
      "mode" -> ignored(Option.empty[play.api.Mode.Value])
    )(Feedback.apply)(Feedback.unapply)
  )
}


