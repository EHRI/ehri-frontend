package services.ingest

import play.api.libs.json.{Json, Reads}

case class FileObject(classifier: String, path: String)
object FileObject {
  implicit val reads: Reads[FileObject] = Json.reads[FileObject]
}


