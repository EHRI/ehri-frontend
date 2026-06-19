package models

import play.api.data.Form
import play.api.data.Forms._
import utils.EnumUtils

case class BatchDeleteTask(
  et: EntityType.Value,
  scope: Option[String] = None,
  version: Boolean = true,
  commit: Boolean = false,
  log: String = "",
  ids: Seq[String] = Seq.empty
)

object BatchDeleteTask {
  val TYPE = "type"
  val VERSION = "version"
  val COMMIT = "commit"
  val SCOPE = "scope"
  val IDS = "ids"
  val LOG_MSG = "logMessage"

  val form: Form[BatchDeleteTask] = Form(
    mapping(
      TYPE -> EnumUtils.enumMapping(EntityType),
      SCOPE -> optional(text),
      VERSION -> boolean,
      COMMIT -> boolean,
      LOG_MSG -> nonEmptyText,
      IDS -> nonEmptyText.transform[Seq[String]](_.split("\r?\n").map(_.trim).toSeq, _.mkString("\n"))
    )(BatchDeleteTask.apply)(BatchDeleteTask.unapply)
  )
}
