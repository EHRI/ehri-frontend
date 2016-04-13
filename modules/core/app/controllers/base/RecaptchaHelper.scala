package controllers.base

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.ws.WSClient
import play.api.mvc.Request

import scala.concurrent.{ExecutionContext, Future}

trait RecaptchaHelper {
  self: ControllerHelpers =>

  def ws: WSClient

  /**
   * Check a capture form.
   */
  def checkRecapture[A](implicit request: Request[A], executionContext: ExecutionContext): Future[Boolean] = {
    // https://developers.google.com/recaptcha/docs/verify
    val recaptchaForm = Form(
      tuple(
        "recaptcha_challenge_field" -> nonEmptyText,
        "recaptcha_response_field" -> nonEmptyText
      )
    )

    // Allow skipping recaptcha checks globally if recaptcha.skip is true
    val skipRecapture = config.getBoolean("recaptcha.skip").getOrElse(false)
    if (skipRecapture) Future.successful(true)
    else {
      recaptchaForm.bindFromRequest.fold({ badCapture =>
        Future.successful(false)
      }, { case (challenge, response) =>
        ws.url("http://www.google.com/recaptcha/api/verify")
          .withQueryString(
            "remoteip" -> request.headers.get("REMOTE_ADDR").getOrElse(""),
            "challenge" -> challenge, "response" -> response,
            "privatekey" -> config.getString("recaptcha.key.private").getOrElse("")
          ).post("").map { response =>
          response.body.split("\n").headOption match {
            case Some("true") => true
            case Some("false") => Logger.logger.error(response.body); false
            case _ => sys.error("Unexpected captcha result: " + response.body)
          }
        }
      })
    }
  }
}
