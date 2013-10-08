package models

import models.base.Model
import org.joda.time.DateTime

import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}
import play.api.libs.json.Json


object DatePeriodF {

  val TYPE = "type"
  val START_DATE = "startDate"
  val END_DATE = "endDate"

  object DatePeriodType extends Enumeration {
    type Type = Value
    val Creation = Value("creation")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  implicit object Converter extends RestConvertable[DatePeriodF] with ClientConvertable[DatePeriodF] {
    lazy val restFormat = models.json.DatePeriodFormat.restFormat
    lazy val clientFormat = Json.format[DatePeriodF]
  }
}

case class DatePeriodF(
  isA: EntityType.Value = EntityType.DatePeriod,
  id: Option[String],
  `type`: Option[DatePeriodF.DatePeriodType.Type],
  startDate: Option[String] = None,
  endDate: Option[String] = None
) extends Model {
  /**
   * Get a string representing the year-range of this period,
   * i.e. 1939-1945
   */
  lazy val years: String = {
    List(startDate, endDate).filter(_.isDefined).flatten.map { dateString =>
      try {
        new DateTime(dateString).getYear
      } catch {
        case _: Throwable => dateString
      }
    }.distinct.mkString("-")
  }
}

