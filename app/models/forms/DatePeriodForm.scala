package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{DatePeriodF, Entity}

/**
 * Date period model form.
 */
object DatePeriodForm {

  import DatePeriodF._

  val form = Form(mapping(
    Entity.ID -> optional(nonEmptyText),
    TYPE -> optional(models.forms.enum(DatePeriodType)),
    START_DATE -> jodaDate("yyyy-MM-dd"),
    END_DATE -> jodaDate("yyyy-MM-dd")
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}
