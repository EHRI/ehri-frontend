package models

import models.base.Model
import org.joda.time.DateTime

import defines.EntityType
import backend.BackendWriteable


object DatePeriodF {

  import models.json._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  val TYPE = "type"
  val START_DATE = "startDate"
  val END_DATE = "endDate"
  val PRECISION = "precision"

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

  implicit val datePeriodReads: Reads[DatePeriodF] = (
    (__ \ ETYPE).readIfEquals(EntityType.DatePeriod) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ TYPE).readNullable[DatePeriodType.Value] and
    (__ \ DATA \ START_DATE).readNullable[String] and
    (__ \ DATA \ END_DATE).readNullable[String] and
    (__ \ DATA \ PRECISION).readNullable[DatePeriodPrecision.Value]
  )(DatePeriodF.apply _)

  implicit val datePeriodWrites = new Writes[DatePeriodF] {
    def writes(d: DatePeriodF): JsValue = {
      Json.obj(
        ID -> d.id,
        ETYPE -> d.isA,
        DATA -> Json.obj(
          TYPE -> d.`type` ,
          START_DATE -> d.startDate,
          END_DATE -> d.endDate,
          PRECISION -> d.precision
        )
      )
    }
  }

  implicit val datePeriodFormat: Format[DatePeriodF] = Format(datePeriodReads,datePeriodWrites)

  implicit object Converter extends BackendWriteable[DatePeriodF] with ClientWriteable[DatePeriodF] {
    lazy val restFormat = datePeriodFormat
    lazy val clientFormat = Json.format[DatePeriodF]
  }
}

case class DatePeriodF(
  isA: EntityType.Value = EntityType.DatePeriod,
  id: Option[String],
  `type`: Option[DatePeriodF.DatePeriodType.Type] = None,
  startDate: Option[String] = None,
  endDate: Option[String] = None,
  precision: Option[DatePeriodF.DatePeriodPrecision.Type] = None
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
    TYPE -> optional(models.forms.enum(DatePeriodType)),
    START_DATE -> optional(text verifying("error.date", dateValidator)),
    END_DATE -> optional(text verifying("error.date", dateValidator)),
    PRECISION -> optional(models.forms.enum(DatePeriodPrecision))
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}
