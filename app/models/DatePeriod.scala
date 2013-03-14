package models

import models.base.Formable
import org.joda.time.DateTime

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites


object DatePeriodType extends Enumeration {
  type Type = Value
  val Creation = Value("creation")
}


object DatePeriodF {
  val TYPE = "type"
  val START_DATE = "startDate"
  val END_DATE = "endDate"
}

case class DatePeriodF(
  val id: Option[String],
  val `type`: Option[DatePeriodType.Type],
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
  import json.DatePeriodFormat._
  lazy val formable: DatePeriodF = Json.toJson(e).as[DatePeriodF]
  lazy val formableOpt: Option[DatePeriodF] = Json.toJson(e).asOpt[DatePeriodF]
}

