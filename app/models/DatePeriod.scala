package models

import models.base.Formable
import org.joda.time.DateTime

import defines.EntityType
import play.api.libs.json.{Format, Json}


object DatePeriodF {

  val TYPE = "type"
  val START_DATE = "startDate"
  val END_DATE = "endDate"

  object DatePeriodType extends Enumeration {
    type Type = Value
    val Creation = Value("creation")
  }

  lazy implicit val datePeriodFormat: Format[DatePeriodF] = json.DatePeriodFormat.restFormat
}

case class DatePeriodF(
  val id: Option[String],
  val `type`: Option[DatePeriodF.DatePeriodType.Type],
  val startDate: Option[String] = None,
  val endDate: Option[String] = None
) {
  val isA = EntityType.DatePeriod

  /**
   * Get a string representing the year-range of this period,
   * i.e. 1939-1945
   */
  lazy val years: String = {
    List(startDate, endDate).filter(_.isDefined).flatten.map { dateString =>
      try {
        new DateTime(dateString).getYear
      } catch {
        case _ => dateString
      }
    }.distinct.mkString("-")
  }
}


case class DatePeriod(val e: Entity) extends Formable[DatePeriodF] {
  lazy val formable: DatePeriodF = Json.toJson(e).as[DatePeriodF]
  lazy val formableOpt: Option[DatePeriodF] = Json.toJson(e).asOpt[DatePeriodF]
}

