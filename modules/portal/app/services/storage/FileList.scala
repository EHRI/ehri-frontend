package services.storage

import play.api.libs.json.{Json, Writes}

case class FileList(
  files: Seq[FileMeta],
  truncated: Boolean
)

object FileList {
  implicit def _writes: Writes[FileList] = Json.writes[FileList]
}
