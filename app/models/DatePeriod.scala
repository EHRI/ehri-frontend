package models

import models.base.{Model, Formable}
import org.joda.time.DateTime

import defines.EntityType
import play.api.libs.json.{Format, Json}
import models.json.{ClientConvertable, RestConvertable}


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
    lazy val restFormat = models.json.rest.datePeriodFormat
    lazy val clientFormat = models.json.client.datePeriodFormat
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
        case _ => dateString
      }
    }.distinct.mkString("-")
  }
}


case class DatePeriod(val e: Entity) extends Formable[DatePeriodF] {
  lazy val formable: DatePeriodF = Json.toJson(e).as[DatePeriodF](json.DatePeriodFormat.restFormat)
  lazy val formableOpt: Option[DatePeriodF] = Json.toJson(e).asOpt[DatePeriodF](json.DatePeriodFormat.restFormat)
}

