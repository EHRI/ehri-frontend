package models

import play.api.data.Form
import play.api.data.Forms._
import play.api.Play._
import play.api.Logger
import play.api.data.validation.{Invalid, Valid, Constraint}

/**
 * Manage signup data.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SignupData(
  name: String,
  email: String,
  password: String,
  confirm: String,
  allowMessaging: Boolean,
  timestamp: String,
  blankCheck: String,
  agreeTerms: Boolean
)

object SignupData {
  val NAME = "name"
  val EMAIL = "email"
  val PASSWORD = "password"
  val CONFIRM = "confirm"
  val ALLOW_MESSAGING = "allowMessaging"
  val TIMESTAMP = "timestamp"
  val BLANK_CHECK = "website" // honeypot
  val AGREE_TERMS = "agreeTerms"

  /**
   * Check submitted form is bound at least 5 seconds after it was
   * first rendered (the minimum amount of time it should take a
   * human to fill a form.)
   */
  private val formSubmissionTime: Constraint[SignupData] = Constraint("constraints.timeCheckSeconds") { data =>
    import org.joda.time.{Seconds, DateTime}
    current.configuration.getInt("ehri.signup.timeCheckSeconds").map { delay =>
      try {
        val renderTime: DateTime = new DateTime(data.timestamp)
        val timeDiff: Int = Seconds.secondsBetween(renderTime, DateTime.now()).getSeconds
        if (timeDiff > delay) Valid
        else {
          Logger.logger.error(s"Bad timestamp on signup for user ${data.name} with delay $delay")
          Invalid("portal.signup.badTimestamp")
        }
      } catch {
        case e: IllegalArgumentException => Invalid("portal.signup.badTimestamp")
      }
    }.getOrElse(Valid)
  }

  /**
   * Ensure the blank check field (hidden on the form) is both
   * present and blank.
   */
  private val blankFieldIsBlank: Constraint[SignupData] = Constraint("constraints.honeypot") { data =>
    if (data.blankCheck.isEmpty) Valid
    else {
      Logger.logger.error(s"Filled blank field on signup for user: ${data.name}")
      Invalid("portal.signup.badSignupInput")
    }
  }

  /**
   * Ensure passwords match.
   */
  private val passwordsMatch: Constraint[SignupData] = Constraint("constraints.passwordMatch") { data =>
    if (data.password == data.confirm) Valid
    else Invalid("portal.signup.badPasswords")
  }

  /**
   * Signup data validation. This does several checks:
   *  - passwords must be over 6 characters
   *  - form must be submitted over 5 seconds after it was rendered
   *  - the blank check field must be present, but left blank (this
   *    is a honeypot check)
   */
  val form = Form(
    mapping(
      NAME -> nonEmptyText,
      EMAIL -> email,
      PASSWORD -> nonEmptyText(minLength = 6),
      CONFIRM -> nonEmptyText(minLength = 6),
      ALLOW_MESSAGING -> ignored(true),
      TIMESTAMP -> text, // submission time check
      BLANK_CHECK -> text, // honeypot
      AGREE_TERMS -> checked("portal.signup.agreeTerms")
    )(SignupData.apply)(SignupData.unapply)
      verifying formSubmissionTime verifying blankFieldIsBlank verifying passwordsMatch
  )
}
