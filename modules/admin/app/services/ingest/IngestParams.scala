package services.ingest

import java.io.File

import defines.EntityType
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files.TemporaryFile


case class IngestParams(
  scopeType: EntityType.Value,
  scope: String,
  fonds: Option[String] = None,
  log: String,
  allowUpdate: Boolean = false,
  tolerant: Boolean = false,
  handler: Option[String] = None,
  importer: Option[String] = None,
  excludes: Seq[String] = Nil,
  file: Option[TemporaryFile] = None,
  properties: Option[TemporaryFile] = None,
  commit: Boolean = false
)

object IngestParams {
  val SCOPE_TYPE = "scope-type"
  val SCOPE = "scope"
  val FONDS = "fonds"
  val TOLERANT = "tolerant"
  val ALLOW_UPDATE = "allow-update"
  val LOG = "log"
  val HANDLER = "handler"
  val IMPORTER = "importer"
  val EXCLUDES = "ex"
  val DATA_FILE = "data"
  val PROPERTIES_FILE = "properties"
  val COMMIT = "commit"

  val ingestForm = Form(
    mapping(
      SCOPE_TYPE -> utils.EnumUtils.enumMapping(EntityType),
      SCOPE -> nonEmptyText,
      FONDS -> optional(nonEmptyText),
      LOG -> nonEmptyText,
      ALLOW_UPDATE -> boolean,
      TOLERANT -> boolean,
      HANDLER -> optional(text),
      IMPORTER -> optional(text),
      EXCLUDES -> optional(text).transform[Seq[String]](
        _.map(_.split("\n").map(_.trim).toSeq).toSeq.flatten,
        s => if(s.isEmpty) None else Some(s.mkString("\n"))),
      DATA_FILE -> ignored(Option.empty[TemporaryFile]),
      PROPERTIES_FILE -> ignored(Option.empty[TemporaryFile]),
      COMMIT -> default(boolean, false)
    )(IngestParams.apply)(IngestParams.unapply)
  )
}
