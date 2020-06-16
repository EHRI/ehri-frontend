package models

import play.api.libs.json.{Format, Json}

case class OaiPmhIdentity(
  name: String,
  url: String,
  version: String
)

object OaiPmhIdentity {
  implicit val _format: Format[OaiPmhIdentity] = Json.format[OaiPmhIdentity]
}


