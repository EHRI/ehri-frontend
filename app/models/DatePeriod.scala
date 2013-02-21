package models

import models.base.Formable
import org.joda.time.DateTime

import play.api.data._
import play.api.data.Forms._

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
) extends Persistable {
  val isA = EntityType.DatePeriod

  /**
   * Get a string representing the year-range of this period,
   * i.e. 1939-1945
   */
  def years: String = {
    List(startDate, endDate).map(_.getYear).distinct.mkString("-")
  }

  def toJson = {
    implicit val dateWrites = play.api.libs.json.Writes.jodaDateWrites("yyyy-MM-dd")

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        DatePeriodF.TYPE -> `type`,
        DatePeriodF.START_DATE -> startDate,
        DatePeriodF.END_DATE -> endDate
      )
    )
  }
}

object DatePeriodForm {

  import DatePeriodF._

  val form = Form(mapping(
    Entity.ID -> optional(nonEmptyText),
    TYPE -> optional(models.forms.enum(DatePeriodType)),
    START_DATE -> jodaDate("yyyy-MM-dd"),
    END_DATE -> jodaDate("yyyy-MM-dd")
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}

case class DatePeriod(val e: Entity) extends Formable[DatePeriodF] {
  def formable: DatePeriodF = new DatePeriodF(
    id = Some(e.id),
    `type` = e.property(DatePeriodF.TYPE).flatMap(defines.enum(DatePeriodType).reads(_).asOpt),
    startDate = e.stringProperty(DatePeriodF.START_DATE).map(new DateTime(_)).getOrElse(sys.error("Invalid date period: " + e)),
    endDate = e.stringProperty(DatePeriodF.END_DATE).map(new DateTime(_)).getOrElse(sys.error("Invalid date period: " + e))
  )
}

