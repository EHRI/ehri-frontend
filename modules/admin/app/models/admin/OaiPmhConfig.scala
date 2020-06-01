package models.admin

import models.Repository.validateUrlPattern
import play.api.data.Form
import play.api.data.Forms._

case class OaiPmhConfig(
  url: String,
  format: String
)

object OaiPmhConfig {
  final val URL = "url"
  final val METADATA_FORMAT = "format"

  val form: Form[OaiPmhConfig] = Form(mapping(
    URL -> nonEmptyText.verifying("errors.badUrlPattern",
      url => utils.forms.isValidUrl(url)
    ),
    METADATA_FORMAT -> nonEmptyText
  )(OaiPmhConfig.apply)(OaiPmhConfig.unapply))
}
