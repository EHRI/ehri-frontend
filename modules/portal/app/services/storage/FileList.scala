package services.storage

import play.api.libs.json.{Json, Format}

case class FileList(
  files: Seq[FileMeta],
  truncated: Boolean
)

object FileList {
  implicit def _format: Format[FileList] = Json.format[FileList]
}
