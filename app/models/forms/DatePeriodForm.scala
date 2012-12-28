package models.forms

import play.api.data._
import play.api.data.Forms._

import models.base.Persistable
import org.joda.time.DateTime
import defines.EntityType


object DatePeriodF {
  val START_DATE = "startDate"
  val END_DATE = "endDate"
}

case class DatePeriodF(
  val id: Option[String],
  val startDate: DateTime,
  val endDate: Option[DateTime] = None
) extends Persistable {
  val isA = EntityType.DatePeriod

  /**
   * Get a string representing the year-range of this period,
   * i.e. 1939-1945
   */
  def years: String = {
    List(Some(startDate), endDate).filter(_.isDefined).map(_.get.getYear).distinct.mkString("-")
  }
}




object DatePeriodForm {

  import DatePeriodF._

  val form = Form(mapping(
    "id" -> optional(nonEmptyText),
    START_DATE -> jodaDate("YYYY-MM-DD"),
    END_DATE -> optional(jodaDate("YYYY=MM-DD"))
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}
