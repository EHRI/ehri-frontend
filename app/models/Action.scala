package models

import defines.EntityType
import defines.PublicationStatus
import defines.enum
import models.base.AccessibleEntity
import models.base.DescribedEntity
import models.base.Description
import models.base.Formable
import models.base.NamedEntity
import models.base.Accessor

case class ActionRepr(val e: Entity) extends AccessibleEntity {
  
  val ACTIONER_REL = "hasActioner"
  
  val timeStamp: String = e.property("timestamp").flatMap(_.asOpt[String]).getOrElse("???") // FIXME: ???
  val logMessage: String = e.property("logMessage").flatMap(_.asOpt[String]).getOrElse("No log message given.")
  val actioner: Option[Accessor] = e.relations(ACTIONER_REL).headOption.map(Accessor(_))
}

