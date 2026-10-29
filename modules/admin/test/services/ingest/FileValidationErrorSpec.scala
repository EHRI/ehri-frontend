package services.ingest

import org.specs2.mutable.Specification
import play.api.libs.json.Json

class FileValidationErrorSpec extends Specification {

  "FileValidationError JSON" should {

    "serialize an XmlValidationError with exactly its own fields (no discriminator)" in {
      val err: FileValidationError = XmlValidationError(1, 6, "some error")
      Json.toJson(err) must_== Json.obj("line" -> 1, "pos" -> 6, "error" -> "some error")
    }

    "serialize a FieldValidationError with exactly its own fields (no discriminator)" in {
      val err: FileValidationError = FieldValidationError("objectIdentifier", "missing", "Required field 'objectIdentifier' is missing")
      Json.toJson(err) must_== Json.obj(
        "field" -> "objectIdentifier", "kind" -> "missing", "error" -> "Required field 'objectIdentifier' is missing")
    }
  }
}
