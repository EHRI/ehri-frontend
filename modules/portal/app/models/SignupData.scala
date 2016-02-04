package models

import utils.forms.{HoneyPotForm, TimeCheckForm}

/**
 * Manage signup data.
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
) extends TimeCheckForm with HoneyPotForm

object SignupData {

  import play.api.data.Form
  import play.api.data.Forms._
  import play.api.data.validation.{Constraint, Invalid, Valid}
  import utils.forms.HoneyPotForm._
  import utils.forms.TimeCheckForm._

  val NAME = "name"
  val EMAIL = "email"
  val PASSWORD = "password"
  val CONFIRM = "confirm"
  val ALLOW_MESSAGING = "allowMessaging"
  val AGREE_TERMS = "agreeTerms"

  /**
   * Ensure passwords match.
   */
  private val passwordsMatch: Constraint[SignupData] = Constraint("constraints.passwordMatch") { data =>
    if (data.password == data.confirm) Valid
    else Invalid("signup.badPasswords")
  }

  /**
   * Signup data validation. This does several checks:
   *  - passwords must be over 6 characters
   *  - form must be submitted over 5 seconds after it was rendered
   *  - the blank check field must be present, but left blank (this
   *    is a honeypot check)
   */
  def form(implicit app: play.api.Application) = Form(
    mapping(
      NAME -> nonEmptyText,
      EMAIL -> email,
      PASSWORD -> nonEmptyText(minLength = 6),
      CONFIRM -> nonEmptyText(minLength = 6),
      ALLOW_MESSAGING -> ignored(true),
      TIMESTAMP -> text, // submission time check
      BLANK_CHECK -> text, // honeypot
      AGREE_TERMS -> checked("signup.agreeTerms")
    )(SignupData.apply)(SignupData.unapply)
      verifying formSubmissionTime verifying blankFieldIsBlank verifying passwordsMatch
  )
}
