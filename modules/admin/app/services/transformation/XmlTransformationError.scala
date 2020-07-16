package services.transformation

import play.api.libs.json.{Json, Writes}

case class XmlTransformationError(
  line: Int,
  pos: Int,
  error: String
) extends Exception(error)

object XmlTransformationError {
  implicit val _writes: Writes[XmlTransformationError] = Json.format[XmlTransformationError]
}
