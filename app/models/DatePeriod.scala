package models

import models.base.Formable
import org.joda.time.DateTime

import defines.EntityType
import play.api.libs.json.Json


object DatePeriodF {

  val TYPE = "type"
  val START_DATE = "startDate"
  val END_DATE = "endDate"

  object DatePeriodType extends Enumeration {
    type Type = Value
    val Creation = Value("creation")
  }

  lazy implicit val datePeriodFormat = json.DatePeriodFormat.datePeriodFormat
}

case class DatePeriodF(
  val id: Option[String],
  val `type`: Option[DatePeriodF.DatePeriodType.Type],
  val startDate: DateTime,
  val endDate: DateTime
) {
  val isA = EntityType.DatePeriod

  /**
   * Get a string representing the year-range of this period,
   * i.e. 1939-1945
   */
  def years: String = {
    List(startDate, endDate).map(_.getYear).distinct.mkString("-")
  }
}


case class DatePeriod(val e: Entity) extends Formable[DatePeriodF] {
  lazy val formable: DatePeriodF = Json.toJson(e).as[DatePeriodF]
  lazy val formableOpt: Option[DatePeriodF] = Json.toJson(e).asOpt[DatePeriodF]
}

