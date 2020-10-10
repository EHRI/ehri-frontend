package models

import java.time.Instant

import play.api.libs.json.{Format, Json}

case class ImportDataset(
  repoId: String,
  id: String,
  name: String,
  src: String,
  created: Instant,
  notes: Option[String] = None
)

object ImportDataset {
  implicit val _format: Format[ImportDataset] = Json.format[ImportDataset]
}

case class ImportDatasetInfo(
  id: String,
  name: String,
  src: String,
  notes: Option[String] = None
)

object ImportDatasetInfo {
  implicit val _format: Format[ImportDatasetInfo] = Json.format[ImportDatasetInfo]
}
