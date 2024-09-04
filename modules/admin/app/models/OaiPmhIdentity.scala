package models

import play.api.libs.json.{Format, Json}
import utils.EnumUtils

case class OaiPmhIdentity(
  name: String,
  url: String,
  version: String,
  granularity: OaiPmhIdentity.Granularity.Value
)

object OaiPmhIdentity {
  object Granularity extends Enumeration {
    val Day = Value("YYYY-MM-DD")
    val Second = Value("YYYY-MM-DDThh:mm:ssZ")

    implicit val _format: Format[models.OaiPmhIdentity.Granularity.Value] = EnumUtils.enumFormat(Granularity)
  }

  implicit val _format: Format[OaiPmhIdentity] = Json.format[OaiPmhIdentity]
}


