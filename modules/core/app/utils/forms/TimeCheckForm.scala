package utils.forms

import play.api.Logger
import play.api.data.validation.{Invalid, Valid, Constraint}

/**
 * A form with a time-check field, i.e. that should not
 * be submitted within a certain time period of being
 * rendered.
 *
 * @author Mike Bryant (http://github.com/mikesname)
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
  def formSubmissionTime[T <: TimeCheckForm](implicit app: play.api.Application): Constraint[T] = {
    Constraint("constraints.timeCheckSeconds") { data =>
      import org.joda.time.{DateTime, Seconds}
      app.configuration.getInt("ehri.signup.timeCheckSeconds").map { delay =>
        try {
          val renderTime: DateTime = new DateTime(data.timestamp)
          val timeDiff: Int = Seconds.secondsBetween(renderTime, DateTime.now()).getSeconds
          if (timeDiff > delay) Valid
          else {
            Logger.logger.error(s"Bad timestamp on signup with delay $delay")
            Invalid("constraints.timeCheckSeconds.failed")
          }
        } catch {
          case e: IllegalArgumentException => Invalid("constraints.timeCheckSeconds.failed")
        }
      }.getOrElse(Valid)
    }
  }
}

