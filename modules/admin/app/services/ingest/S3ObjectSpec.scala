package services.ingest

import play.api.libs.json.{Json, Reads}

case class S3ObjectSpec(classifier: String, path: String)
object S3ObjectSpec {
  implicit val reads: Reads[S3ObjectSpec] = Json.reads[S3ObjectSpec]
}


