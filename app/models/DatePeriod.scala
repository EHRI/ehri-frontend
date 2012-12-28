package models

import models.base.Formable
import org.joda.time.DateTime

import models.forms.DatePeriodF

case class DatePeriod(val e: Entity) extends Formable[DatePeriodF] {
  def to: DatePeriodF = new DatePeriodF(
    id = Some(e.id),
    startDate = e.stringProperty(DatePeriodF.START_DATE).map(new DateTime(_)),
    endDate = e.stringProperty(DatePeriodF.END_DATE).map(new DateTime(_))
  )
}

