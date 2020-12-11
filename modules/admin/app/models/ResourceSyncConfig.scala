package models

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, optional}

case class ResourceSyncConfig(
  url: String,
  filter: Option[String] = None
)


object ResourceSyncConfig {
  final val URL = "url"
  final val FILTER = "filter"

  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  implicit val _reads: Reads[ResourceSyncConfig] = (
    (__ \ URL).read(Reads.filter(
      JsonValidationError("errors.badUrlPattern"))(url => utils.forms.isValidUrl(url))) and
      (__ \ FILTER).readNullable[String]
    )(ResourceSyncConfig.apply _)

  implicit val _writes: Writes[ResourceSyncConfig] = Json.writes[ResourceSyncConfig]
  implicit val _format: Format[ResourceSyncConfig] = Format(_reads, _writes)

  val form: Form[ResourceSyncConfig] = Form(mapping(
    URL -> nonEmptyText.verifying("errors.badUrlPattern",
      url => utils.forms.isValidUrl(url)),
    FILTER -> optional(nonEmptyText)
  )(ResourceSyncConfig.apply)(ResourceSyncConfig.unapply))
}
