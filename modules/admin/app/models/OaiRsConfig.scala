package models

import java.net.URI

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, optional}

case class OaiRsConfig(
  changeList: URI,
  filter: Option[String] = None
)


object OaiRsConfig {
  final val URL = "url"
  final val FILTER = "filter"

  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  implicit val _reads: Reads[OaiRsConfig] = (
    (__ \ URL).read(Reads.filter(
      JsonValidationError("errors.badUrlPattern"))(url => utils.forms.isValidUrl(url))).map(URI.create) and
      (__ \ FILTER).readNullable[String]
    )(OaiRsConfig.apply _)

  implicit val _writes: Writes[OaiRsConfig] = Json.writes[OaiRsConfig]
  implicit val _format: Format[OaiRsConfig] = Format(_reads, _writes)

  val form: Form[OaiRsConfig] = Form(mapping(
    URL -> nonEmptyText.verifying("errors.badUrlPattern",
      url => utils.forms.isValidUrl(url)
    ).transform[URI](URI.create, s => s.toString),
    FILTER -> optional(nonEmptyText)
  )(OaiRsConfig.apply)(OaiRsConfig.unapply))
}
