package models

import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.time.{LocalDate, Year, YearMonth}

import defines.EntityType
import models.base.Model
import services.data.Writable


object DatePeriodF {

  import models.json._
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  val TYPE = "type"
  val START_DATE = "startDate"
  val END_DATE = "endDate"
  val PRECISION = "precision"
  val DESCRIPTION = "description"

  object DatePeriodType extends Enumeration {
    type Type = Value
    val Creation = Value("creation")
    val Existence = Value("existence")

    implicit val format: Format[DatePeriodType.Value] = defines.EnumUtils.enumFormat(this)
  }

  object DatePeriodPrecision extends Enumeration {
    type Type = Value
    val Year = Value("year")
    val Quarter = Value("quarter")
    val Month = Value("month")
    val Week = Value("week")
    val Day = Value("day")

    implicit val format: Format[DatePeriodPrecision.Value] = defines.EnumUtils.enumFormat(this)
  }

  import Entity.{TYPE => ETYPE, _}

  implicit val datePeriodFormat: Format[DatePeriodF] = (
    (__ \ ETYPE).formatIfEquals(EntityType.DatePeriod) and
      (__ \ ID).formatNullable[String] and
      (__ \ DATA \ TYPE).formatNullable[DatePeriodType.Value] and
      (__ \ DATA \ START_DATE).formatNullable[String] and
      (__ \ DATA \ END_DATE).formatNullable[String] and
      (__ \ DATA \ PRECISION).formatNullable[DatePeriodPrecision.Value] and
      (__ \ DATA \ DESCRIPTION).formatNullable[String]
    ) (DatePeriodF.apply, unlift(DatePeriodF.unapply))

  implicit object Converter extends Writable[DatePeriodF] {
    lazy val restFormat: Format[DatePeriodF] = datePeriodFormat
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
    * i.e. 1939-1945.
    */
  lazy val years: String = Seq(startDate, endDate).collect {
    case Some(d) if d.matches("^\\d{4}.*") => d.substring(0, 4)
  }.distinct.mkString("-")
}

object DatePeriod {

  import DatePeriodF._
  import Entity.{TYPE => _, _}
  import defines.EnumUtils.enumMapping

  val formats: List[String => TemporalAccessor] = List(
    LocalDate.parse, YearMonth.parse, Year.parse
  )

  def tryFormats(s: String, formats: List[String => TemporalAccessor]): Boolean = formats match {
    case f :: rest => try {
      f.apply(s)
      true
    } catch {
      case e: DateTimeParseException => tryFormats(s, rest)
    }
    case Nil => false
  }

  val dateValidator: (String) => Boolean = date => tryFormats(date, formats)

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
