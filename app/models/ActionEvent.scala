package models

import models.base.AccessibleEntity
import models.base.Accessor
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

case class ActionEvent(val e: Entity) extends AccessibleEntity {

  val ACTIONER_REL = "hasActioner"
  val ACTION_REL = "hasEventAction"
  val SUBJECT_REL = "hasSubject"

  val timeStamp: DateTime = e.property("timestamp").flatMap(_.asOpt[String]).map(new DateTime(_))
    .getOrElse(sys.error("No timestamp found on action [%s]".format(e.id)))
  val logMessage: String = e.property("logMessage").flatMap(_.asOpt[String]).getOrElse("No log message given.")
  val action: Option[ActionLog] = e.relations(ACTION_REL).headOption.map(ActionLog(_))
  val subject: Option[ItemWithId] = e.relations(SUBJECT_REL).headOption.map(ItemWithId)

  def time = {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:MM:ss").print(timeStamp)
  }
}

