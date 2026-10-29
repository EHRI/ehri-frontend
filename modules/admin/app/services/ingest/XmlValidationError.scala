package services.ingest

import play.api.libs.json.{Json, Writes}

/**
  * A validation error from any validator format (XML schema, tabular/JSON field
  * check, ...). No discriminator field is added on the wire - the two variants'
  * fields don't overlap (besides "error"), so they're already distinguishable
  * by shape, and adding one would change the existing XML validation response
  * format that real callers already depend on.
  */
sealed trait FileValidationError

object FileValidationError {
  implicit val _writes: Writes[FileValidationError] = Writes {
    case e: XmlValidationError => Json.toJson(e)(XmlValidationError._writes)
    case e: FieldValidationError => Json.toJson(e)(FieldValidationError._writes)
  }
}

case class XmlValidationError(
  line: Int,
  pos: Int,
  error: String
) extends FileValidationError

object XmlValidationError {
  implicit val _writes: Writes[XmlValidationError] = Json.writes[XmlValidationError]
}

case class FieldValidationError(field: String, kind: String, error: String) extends FileValidationError

object FieldValidationError {
  implicit val _writes: Writes[FieldValidationError] = Json.writes[FieldValidationError]
}
