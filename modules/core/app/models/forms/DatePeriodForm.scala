package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{DatePeriodF, Entity}
import org.joda.time.DateTime
import defines.EntityType

/**
 * Date period model form.
 */
object DatePeriodForm {

  import DatePeriodF._

  private val dateValidator: (String) => Boolean = { dateString =>
    try {
      DateTime.parse(dateString)
      true
    } catch {
      case e: IllegalArgumentException => false
    }
  }

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.DatePeriod),
    Entity.ID -> optional(nonEmptyText),
    TYPE -> optional(models.forms.enum(DatePeriodType)),
    START_DATE -> optional(text verifying("error.date", dateValidator)),
    END_DATE -> optional(text verifying("error.date", dateValidator))
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}
