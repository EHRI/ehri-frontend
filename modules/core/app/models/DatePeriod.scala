package models

import models.base.Model
import org.joda.time.DateTime

import defines.EntityType
import backend.{Entity, Writable}


object DatePeriodF {

  import models.json._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  val TYPE = "type"
  val START_DATE = "startDate"
  val END_DATE = "endDate"
  val PRECISION = "precision"
  val DESCRIPTION = "description"

  object DatePeriodType extends Enumeration {
    type Type = Value
    val Creation = Value("creation")
    val Existence = Value("existence")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  object DatePeriodPrecision extends Enumeration {
    type Type = Value
    val Year = Value("year")
    val Quarter = Value("quarter")
    val Month = Value("month")
    val Week = Value("week")
    val Day = Value("day")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  import Entity.{TYPE => ETYPE,_}

  implicit val datePeriodFormat: Format[DatePeriodF] = (
    (__ \ ETYPE).formatIfEquals(EntityType.DatePeriod) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ TYPE).formatNullable[DatePeriodType.Value] and
    (__ \ DATA \ START_DATE).formatNullable[String] and
    (__ \ DATA \ END_DATE).formatNullable[String] and
    (__ \ DATA \ PRECISION).formatNullable[DatePeriodPrecision.Value] and
    (__ \ DATA \ DESCRIPTION).formatNullable[String]
  )(DatePeriodF.apply, unlift(DatePeriodF.unapply))

  implicit object Converter extends Writable[DatePeriodF] {
    lazy val restFormat = datePeriodFormat
  }
}

case class DatePeriodF(
  isA: EntityType.Value = EntityType.DatePeriod,
  id: Option[String],
  `type`: Option[DatePeriodF.DatePeriodType.Type] = None,
  startDate: Option[String] = None,
  endDate: Option[String] = None,
  precision: Option[DatePeriodF.DatePeriodPrecision.Type] = None,
  description: Option[String] = None
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

object DatePeriod {
  import DatePeriodF._
  import Entity.{TYPE => _, _}
  import defines.EnumUtils.enumMapping

  private val dateValidator: (String) => Boolean = { dateString =>
    try {
      DateTime.parse(dateString)
      true
    } catch {
      case e: IllegalArgumentException => false
    }
  }

  import play.api.data.Form
  import play.api.data.Forms._

  val form = Form(mapping(
    ISA -> ignored(EntityType.DatePeriod),
    ID -> optional(nonEmptyText),
    TYPE -> optional(enumMapping(DatePeriodType)),
    START_DATE -> optional(text verifying("error.date", dateValidator)),
    END_DATE -> optional(text verifying("error.date", dateValidator)),
    PRECISION -> optional(enumMapping(DatePeriodPrecision)),
    DESCRIPTION -> optional(text)
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}
