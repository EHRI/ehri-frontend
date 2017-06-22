package models.admin

import java.io.File

import play.api.data.Form
import play.api.data.Forms._

case class IngestTask(
  log: String,
  allowUpdate: Boolean = false,
  tolerant: Boolean = false,
  file: Option[File] = None,
  properties: Option[File] = None
)

object IngestTask {

  val TOLERANT = "tolerant"
  val ALLOW_UPDATE = "allow-update"
  val LOG = "log"
  val DATA_FILE = "data"
  val PROPERTIES_FILE = "properties"

  val form = Form(
    mapping(
      LOG -> nonEmptyText,
      ALLOW_UPDATE -> boolean,
      TOLERANT -> boolean,
      DATA_FILE -> ignored(Option.empty[File]),
      PROPERTIES_FILE -> ignored(Option.empty[File])
    )(IngestTask.apply)(IngestTask.unapply)
  )
}
