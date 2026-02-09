package models

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.{Json, JsonConfiguration, Writes}


sealed trait ConfigHandle
case class FileConfig(f: Option[TemporaryFile]) extends ConfigHandle {
  override def toString: String = f.map(_.path.getFileName.toString).getOrElse("-")
}
case class UrlConfig(url: String) extends ConfigHandle {
  override def toString: String = url
}

object ConfigHandle {
  def apply(f: Option[TemporaryFile]): ConfigHandle = FileConfig(f)
  def apply(url: String): ConfigHandle = UrlConfig(url)
  def empty: ConfigHandle = FileConfig(Option.empty)

  implicit val _writes: Writes[ConfigHandle] = Writes {
    case FileConfig(f) => Json.toJson(f.map(_.toAbsolutePath.toString))
    case UrlConfig(url) => Json.toJson(url)
  }
}

sealed trait PayloadHandle
case class UrlMapPayload(urls: Map[String, java.net.URI]) extends PayloadHandle
case class FilePayload(f: Option[TemporaryFile]) extends PayloadHandle

object PayloadHandle {
  def apply(f: Option[TemporaryFile]): PayloadHandle = FilePayload(f)
  def apply(urls: Map[String, java.net.URI]): PayloadHandle = UrlMapPayload(urls)
  def empty: PayloadHandle = FilePayload(Option.empty)

  implicit val _writes: Writes[PayloadHandle] = Writes {
    case FilePayload(f) => Json.toJson(f.map(_.toAbsolutePath.toString))
    case UrlMapPayload(urls) => Json.toJson(urls.view.mapValues(_.toString).toMap)
  }
}

case class IngestParams(
  scopeType: ContentTypes.Value,
  scope: String,
  log: String,
  fonds: Option[String] = None,
  lang: Option[String] = None,
  allowUpdate: Boolean = false,
  useSourceId: Boolean = false,
  tolerant: Boolean = false,
  handler: Option[String] = None,
  importer: Option[String] = None,
  excludes: Seq[String] = Nil,
  baseURI: Option[String] = None,
  suffix: Option[String] = None,
  data: PayloadHandle = PayloadHandle.empty,
  properties: ConfigHandle = ConfigHandle.empty,
  hierarchyFile: ConfigHandle = ConfigHandle.empty,
  commit: Boolean = false
)

object IngestParams {
  val SCOPE_TYPE = "scope-type"
  val SCOPE = "scope"
  val FONDS = "fonds"
  val TOLERANT = "tolerant"
  val ALLOW_UPDATE = "allow-update"
  val USE_SOURCE_ID = "use-source-id"
  val LANG = "lang"
  val HIERARCHY_FILE = "hierarchy-file"
  val LOG = "log"
  val HANDLER = "handler"
  val IMPORTER = "importer"
  val EXCLUDES = "ex"
  val DATA_FILE = "data"
  val BASE_URI = "baseURI"
  val SUFFIX = "suffix"
  val PROPERTIES_FILE = "properties"
  val COMMIT = "commit"

  val ingestForm: Form[IngestParams] = Form(
    mapping(
      SCOPE_TYPE -> utils.EnumUtils.enumMapping(ContentTypes),
      SCOPE -> nonEmptyText,
      LOG -> nonEmptyText,
      FONDS -> optional(nonEmptyText),
      LANG -> optional(text),
      ALLOW_UPDATE -> boolean,
      USE_SOURCE_ID -> boolean,
      TOLERANT -> boolean,
      HANDLER -> optional(text),
      IMPORTER -> optional(text),
      EXCLUDES -> optional(text).transform[Seq[String]](
        _.map(_.split("\r?\n").map(_.trim).toSeq).toSeq.flatten,
        s => if(s.isEmpty) None else Some(s.mkString("\n"))),
      BASE_URI -> optional(text),
      SUFFIX -> optional(text),
      DATA_FILE -> ignored(PayloadHandle.empty),
      PROPERTIES_FILE -> ignored(ConfigHandle.empty),
      HIERARCHY_FILE -> ignored(ConfigHandle.empty),
      COMMIT -> default(boolean, false)
    )(IngestParams.apply)(IngestParams.unapply)
  )

  private implicit val config: Aux[Json.MacroOptions] = JsonConfiguration(SnakeCase)
  implicit val _writes: Writes[IngestParams] = Json.writes[IngestParams]
}
