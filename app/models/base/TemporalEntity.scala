package models.base

import models.DatePeriod
import models.Entity

object TemporalEntity {
  final val DATE_REL = "hasDate"


}

trait TemporalEntity {

  val e: Entity
  
  val dates: List[DatePeriod] = e.relations(TemporalEntity.DATE_REL).map(DatePeriod(_))
  
  lazy val dateString: Option[String] = dates.flatMap(_.formableOpt.map(_.years)) match {
    case Nil => None
    case lst @ _ => Some(lst.mkString(", "))
  }    
}