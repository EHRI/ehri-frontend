package models

import java.time.Instant

import play.api.libs.json.{Format, Json}
import utils.EnumUtils
import utils.db.StorableEnum

case class ImportDataset(
  repoId: String,
  id: String,
  name: String,
  src: ImportDataset.Src.Value,
  created: Instant,
  fonds: Option[String] = None,
  sync: Boolean = false,
  notes: Option[String] = None,
)

object ImportDataset {
  object Src extends Enumeration with StorableEnum {
    val Upload = Value("upload")
    val OaiPmh = Value("oaipmh")
    val Rs = Value("rs")

    implicit val _format: Format[ImportDataset.Src.Value] = EnumUtils.enumFormat(ImportDataset.Src)
  }

  implicit val _format: Format[ImportDataset] = Json.format[ImportDataset]
}

case class ImportDatasetInfo(
  id: String,
  name: String,
  src: ImportDataset.Src.Value,
  fonds: Option[String] = None,
  sync: Boolean = false,
  notes: Option[String] = None
)

object ImportDatasetInfo {
  implicit val _format: Format[ImportDatasetInfo] = Json.format[ImportDatasetInfo]
}
