package forms

import play.api.data.validation.{Constraint, Invalid, Valid}

/**
 * A form with a honey pot field, i.e. one that should
 * not be filled with a non-blank value by an actual-person
 * user.
 */
trait HoneyPotForm {
  def blankCheck: String
}

object HoneyPotForm {
  val BLANK_CHECK = "website" // honeypot

  /**
   * Ensure the blank check field (hidden on the form) is both
   * present and blank.
   */
  def blankFieldIsBlank[T <: HoneyPotForm]: Constraint[T] = Constraint("constraints.honeypot") { data =>
    if (data.blankCheck.isEmpty) Valid
    else Invalid("constraints.honeypot.failed")
  }
}

