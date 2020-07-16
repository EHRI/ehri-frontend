package services.transformation

import play.api.libs.json.{Json, Writes}

case class InvalidMappingError(
  error: String
) extends Exception(error)

object InvalidMappingError {
  implicit val _writes: Writes[InvalidMappingError] = Json.writes[InvalidMappingError]
}


