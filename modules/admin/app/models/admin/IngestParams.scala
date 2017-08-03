package models.admin

import java.io.File

import play.api.data.Form
import play.api.data.Forms._

case class IngestParams(
  log: String,
  allowUpdate: Boolean = false,
  tolerant: Boolean = false,
  handler: Option[String] = None,
  importer: Option[String] = None,
  file: Option[File] = None,
  properties: Option[File] = None
) {
  def toParams: Seq[(String, String)] = {
    import IngestParams._
    Seq(
      TOLERANT -> tolerant.toString,
      ALLOW_UPDATE -> allowUpdate.toString,
      LOG -> log) ++
    handler.map(HANDLER -> _).toSeq ++
    importer.map(IMPORTER -> _).toSeq ++
    // NB: Hack that assumes the server is on the same
    // host and we really shouldn't do this!
    properties.map(PROPERTIES_FILE -> _.getAbsolutePath)
  }
}

object IngestParams {
  val TOLERANT = "tolerant"
  val ALLOW_UPDATE = "allow-update"
  val LOG = "log"
  val HANDLER = "handler"
  val IMPORTER = "importer"
  val DATA_FILE = "data"
  val PROPERTIES_FILE = "properties"

  val ingestForm = Form(
    mapping(
      LOG -> nonEmptyText,
      ALLOW_UPDATE -> boolean,
      TOLERANT -> boolean,
      HANDLER -> optional(text),
      IMPORTER -> optional(text),
      DATA_FILE -> ignored(Option.empty[File]),
      PROPERTIES_FILE -> ignored(Option.empty[File])
    )(IngestParams.apply)(IngestParams.unapply)
  )
}
