package models.view

import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Call

case class MessagingInfo(
  canMessage: Boolean,
  form: Form[(String, String, Boolean)],
  action: Call,
  recapchaKey: String
)

object MessagingInfo {
  def apply(recipientId: String)(implicit config: Configuration): MessagingInfo = MessagingInfo(
    canMessage = false,
    form = Form(
      tuple(
        "subject" -> nonEmptyText,
        "message" -> nonEmptyText,
        "copySelf" -> default(boolean, false)
      )
    ),
    action = controllers.portal.social.routes.Social.sendMessagePost(recipientId),
    recapchaKey = config.getOptional[String]("recaptcha.key.public")
        .getOrElse("fakekey")
  )
}
