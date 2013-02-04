package models.forms

import play.api.data._
import play.api.data.Forms._

import models.Entity
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

  def toJson = {
    implicit val dateWrites = play.api.libs.json.Writes.jodaDateWrites("yyyy-MM-dd")

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
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
    START_DATE -> optional(jodaDate("yyyy-MM-dd")),
    END_DATE -> optional(jodaDate("yyyy-MM-dd"))
  )(DatePeriodF.apply)(DatePeriodF.unapply))
}
