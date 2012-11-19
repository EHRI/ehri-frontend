package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines.EntityType
import models.base.AccessibleEntity
import models.base.Accessor
import models.base.NamedEntity
import models.base.Formable
import models.base.Persistable
import models.base.Field
import models.base.Field._
import org.joda.time.DateTime

case class DatePeriodRepr(val e: Entity) extends Formable[DatePeriod] {
  def to: DatePeriod = new DatePeriod(
    id = Some(e.id),
    startDate = e.stringProperty(DatePeriod.START_DATE).map(new DateTime(_))
    					.getOrElse(sys.error("No start date defined date period [%s]".format(e.id))),
    endDate = e.stringProperty(DatePeriod.END_DATE).map(new DateTime(_))
  )
}

object DatePeriod {
    
  val START_DATE = Field("startDate", "Start Date")
  val END_DATE = Field("endDate", "End Date")
}

case class DatePeriod(
  val id: Option[Long],
  val startDate: DateTime,
  val endDate: Option[DateTime] = None
) extends Persistable {
  val isA = EntityType.DatePeriod  
  
  /**
   * Get a string representing the year-range of this period,
   * i.e. 1939-1945
   */
  def years: String = {
    List(Some(startDate), endDate).filter(_.isDefined).map(_.get.getYear).distinct.mkString("-")
  }
}


