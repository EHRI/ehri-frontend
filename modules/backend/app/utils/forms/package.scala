package utils

import play.api.mvc.{AnyContent, Request}
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import play.api.data.Forms._
import play.api.Play._
import play.api.libs.ws.WS
import play.api.Logger
import java.net.{MalformedURLException, URL}
import backend.Entity
import play.api.data.validation.{Invalid, Valid, Constraint}

/**
 * Form-related utilities
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object forms {


  val BLANK_CHECK = "website" // honeypot
  val TIMESTAMP = "timestamp"

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

  trait HoneyPotForm {
    val blankCheck: String
  }

  trait TimeCheckForm {
    val timestamp: String
  }

  /**
   * Check submitted form is bound at least 5 seconds after it was
   * first rendered (the minimum amount of time it should take a
   * human to fill a form.)
   */
  def formSubmissionTime[T <: TimeCheckForm](implicit app: play.api.Application): Constraint[T] = {
    Constraint("constraints.timeCheckSeconds") { data =>
      import org.joda.time.{Seconds, DateTime}
      app.configuration.getInt("ehri.signup.timeCheckSeconds").map { delay =>
        try {
          val renderTime: DateTime = new DateTime(data.timestamp)
          val timeDiff: Int = Seconds.secondsBetween(renderTime, DateTime.now()).getSeconds
          if (timeDiff > delay) Valid
          else {
            Logger.logger.error(s"Bad timestamp on signup with delay $delay")
            Invalid("constraits.timeCheckSeconds.failed")
          }
        } catch {
          case e: IllegalArgumentException => Invalid("constraits.timeCheckSeconds.failed")
        }
      }.getOrElse(Valid)
    }
  }

  /**
   * Ensure the blank check field (hidden on the form) is both
   * present and blank.
   */
  def blankFieldIsBlank[T <: HoneyPotForm]: Constraint[T] = Constraint("constraints.honeypot") { data =>
    if (data.blankCheck.isEmpty) Valid
    else Invalid("constraints.honeypot.failed")
  }

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

  import play.api.libs.json.Json
  import play.api.data.format.Formatter
  import play.api.data.{ FormError, Forms, Mapping }

  /**
   * Constructs a simple mapping for a text field (mapped as `scala.Enumeration`)
   *
   * For example:
   * {{{
   *   Form("status" -> enum(Status))
   * }}}
   *
   * @param enum the Enumeration#Value
   */
  def enum[E <: Enumeration](enum: E): Mapping[E#Value] = Forms.of(enumFormat(enum))

  /**
   * Default formatter for `scala.Enumeration`
   *
   */
  def enumFormat[E <: Enumeration](enum: E): Formatter[E#Value] = new Formatter[E#Value] {
    def bind(key: String, data: Map[String, String]) = {
      play.api.data.format.Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[E#Value]
          .either(enum.withName(s))
          .left.map(e => Seq(FormError(key, "error.enum", Nil)))
      }
    }
    def unbind(key: String, value: E#Value) = Map(key -> value.toString)
  }

  /**
   * Constructs a simple mapping for a text field (mapped as `JsObject`)
   *
   * For example:
   * {{{
   *   Form("randomData" -> jsonObj(Status))
   * }}}
   */
  def entity: Mapping[Entity] = Forms.of(entityMapping)

  /**
   * Default formatter for `scala.Enumeration`
   *
   */
  def entityMapping: Formatter[Entity] = new Formatter[Entity] {
    def bind(key: String, data: Map[String, String]) = {
      play.api.data.format.Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[Entity]
          .either(Json.parse(s).as[Entity](Entity.entityReads))
          .left.map(e => Seq(FormError(key, "error.jsonObj", Nil)))
      }
    }
    def unbind(key: String, value: Entity) = Map(key -> Json.stringify(Json.toJson(value)(Entity.entityWrites)))
  }

}