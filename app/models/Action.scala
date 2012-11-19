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
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat

case class ActionRepr(val e: Entity) extends AccessibleEntity {

  val ACTIONER_REL = "hasActioner"

  val timeStamp: DateTime = e.property("timestamp").flatMap(_.asOpt[String]).map(new DateTime(_))
    .getOrElse(sys.error("No timestamp found on action [%s]".format(e.id)))
  val logMessage: String = e.property("logMessage").flatMap(_.asOpt[String]).getOrElse("No log message given.")
  val actioner: Option[Accessor] = e.relations(ACTIONER_REL).headOption.map(Accessor(_))
  
  def time = {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:MM:SS").print(timeStamp)
  }
}

