package models

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Format, Json}

case class OaiPmhConfig(
  url: String,
  format: String,
  set: Option[String] = None
)

object OaiPmhConfig {
  final val URL = "url"
  final val METADATA_FORMAT = "format"
  final val SET = "set"

  implicit val _format: Format[OaiPmhConfig] = Json.format[OaiPmhConfig]

  val form: Form[OaiPmhConfig] = Form(mapping(
    URL -> nonEmptyText.verifying("errors.badUrlPattern",
      url => utils.forms.isValidUrl(url)
    ),
    METADATA_FORMAT -> nonEmptyText,
    SET -> optional(text).transform[Option[String]](_.filterNot(_.trim.isEmpty), identity)

  )(OaiPmhConfig.apply)(OaiPmhConfig.unapply))
}
