package services.storage

import java.time.Instant

import play.api.libs.json.{Json, Writes}

/**
  * A representation of a stored file.
  *
  * @param key          the unique key of this file
  * @param lastModified the last modified time
  * @param size         the size, in bytes
  */
case class FileMeta(
  key: String,
  lastModified: Instant,
  size: Long
)

object FileMeta {
  implicit val _toJson: Writes[FileMeta] = Json.writes[FileMeta]
}
