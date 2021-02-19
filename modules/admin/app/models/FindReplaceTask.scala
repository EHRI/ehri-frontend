package models

import play.api.data.Form
import play.api.data.Forms._

case class FindReplaceTask(
  parentType: ContentTypes.Value,
  subType: EntityType.Value,
  property: String,
  find: String,
  replace: String,
  log: Option[String]
)

object FindReplaceTask {

  val PARENT_TYPE = "type"
  val SUB_TYPE = "subtype"
  val PROPERTY = "property"
  val FIND = "from"
  val REPLACE = "to"
  val LOG_MSG = "logMessage"

  val form: Form[FindReplaceTask] = Form(
    mapping(
      PARENT_TYPE -> nonEmptyText.transform[ContentTypes.Value](ContentTypes.withName, _.toString),
      SUB_TYPE -> nonEmptyText.transform[EntityType.Value](EntityType.withName, _.toString),
      PROPERTY -> nonEmptyText,
      FIND -> nonEmptyText,
      REPLACE -> nonEmptyText,
      LOG_MSG -> optional(nonEmptyText)
    )(FindReplaceTask.apply)(FindReplaceTask.unapply)
  )
}
