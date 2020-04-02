package services.ingest

import play.api.libs.json.{Json, Writes}

case class XmlValidationError(
  line: Int,
  pos: Int,
  error: String
)

object XmlValidationError {
  implicit val _writes: Writes[XmlValidationError] = Json.writes[XmlValidationError]
}
