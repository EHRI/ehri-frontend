package models.base

import models.DatePeriodRepr
import models.Entity

trait TemporalEntity {

  val e: Entity
  
  final val DATE_REL = "hasDate"

  val dates: List[DatePeriodRepr] = e.relations(DATE_REL).map(DatePeriodRepr(_))
  
  lazy val dateString: Option[String] = dates.map(_.to.years) match {
    case Nil => None
    case lst @ _ => Some(lst.mkString(", "))
  }    
}