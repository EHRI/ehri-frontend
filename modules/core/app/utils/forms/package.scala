package utils

import play.api.mvc.{AnyContent, Request}
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import play.api.data.Forms._
import play.api.Play._
import play.api.libs.ws.WS
import play.api.Logger
import java.net.{MalformedURLException, URL}

/**
 * Form-related utilities
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object forms {

  /**
   * Check if a string is a valid URL.
   * @param s url string
   * @return
   */
  def isValidUrl(s: String): Boolean = {
    try {
      new URL(s)
      true
    } catch {
      case s: MalformedURLException => false
    }
  }

  /**
   * Check a capture form.
   */
  def checkRecapture(implicit request: Request[AnyContent], executionContext: ExecutionContext): Future[Boolean] = {
    // https://developers.google.com/recaptcha/docs/verify
    val recaptchaForm = Form(
      tuple(
        "recaptcha_challenge_field" -> nonEmptyText,
        "recaptcha_response_field" -> nonEmptyText
      )
    )

    // Allow skipping recaptcha checks globally if recaptcha.skip is true
    val skipRecapture = current.configuration.getBoolean("recaptcha.skip").getOrElse(false)
    if (skipRecapture) Future.successful(true)
    else {
      recaptchaForm.bindFromRequest.fold({ badCapture =>
        Future.successful(false)
      }, { case (challenge, response) =>
        WS.url("http://www.google.com/recaptcha/api/verify")
          .withQueryString(
          "remoteip" -> request.headers.get("REMOTE_ADDR").getOrElse(""),
          "challenge" -> challenge, "response" -> response,
          "privatekey" -> current.configuration.getString("recaptcha.key.private").getOrElse("")
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
