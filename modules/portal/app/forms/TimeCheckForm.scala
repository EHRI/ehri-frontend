package forms

import java.time.format.DateTimeParseException

import play.api.data.validation.{Constraint, Invalid, Valid}

/**
 * A form with a time-check field, i.e. that should not
 * be submitted within a certain time period of being
 * rendered.
 */
trait TimeCheckForm {
  def timestamp: String
}

object TimeCheckForm {

  val TIMESTAMP = "timestamp"

  /**
   * Check submitted form is bound at least 5 seconds after it was
   * first rendered (the minimum amount of time it should take a
   * human to fill a form.)
   */
  def formSubmissionTime[T <: TimeCheckForm](implicit config: play.api.Configuration): Constraint[T] = {
    Constraint("constraints.timeCheckSeconds") { data =>
      import java.time.ZonedDateTime
      import java.time.temporal.ChronoUnit

      config.getOptional[Int]("ehri.signup.timeCheckSeconds").map { delay =>
        try {
          val renderTime: ZonedDateTime = ZonedDateTime.parse(data.timestamp)
          val diff: Long = renderTime.until(ZonedDateTime.now, ChronoUnit.SECONDS)
          if (diff > delay) Valid
          else Invalid("constraints.timeCheckSeconds.failed")
        } catch {
          case e: DateTimeParseException => Invalid("constraints.timeCheckSeconds.failed")
        }
      }.getOrElse(Valid)
    }
  }
}

