package services.storage

import java.time.Instant

import play.api.libs.json.{Json, Format}

/**
  * A representation of a stored file.
  *
  * @param key          the unique key of this file
  * @param lastModified the last modified time
  * @param size         the size, in bytes
  * @param eTag         the identifier for the specific version of this file
  * @param contentType  the mime-type string
  */
case class FileMeta(
  classifier: String,
  key: String,
  lastModified: Instant,
  size: Long,
  eTag: Option[String] = None,
  contentType: Option[String] = None
)

object FileMeta {
  implicit val _format: Format[FileMeta] = Json.format[FileMeta]
}
