package models.forms

import play.api.data._
import play.api.data.Forms._

import models.base.Persistable
import org.joda.time.DateTime
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites


object DatePeriodF {
  val START_DATE = "startDate"
  val END_DATE = "endDate"
}

case class DatePeriodF(
  val id: Option[String],
  val startDate: Option[DateTime],
  val endDate: Option[DateTime] = None
) extends Persistable {
  val isA = EntityType.DatePeriod

  /**
   * Get a string representing the year-range of this period,
   * i.e. 1939-1945
   */
  def years: String = {
    List(startDate, endDate).filter(_.isDefined).map(_.get.getYear).distinct.mkString("-")
  }

  def toJson = Json.obj(
    DatePeriodF.START_DATE -> startDate,
    DatePeriodF.END_DATE -> endDate
  )
}




object DatePeriodForm {

  import DatePeriodF._

  val form = Form(mapping(
    "id" -> optional(nonEmptyText),
    START_DATE -> optional(jodaDate("YYYY-MM-DD")),
    END_DATE -> optional(jodaDate("YYYY-MM-DD"))
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}
