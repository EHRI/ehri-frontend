package models

import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json._
import utils.EnumUtils
import utils.db.StorableEnum

import java.time.Instant

case class ImportDataset(
  repoId: String,
  id: String,
  name: String,
  src: ImportDataset.Src.Value,
  contentType: Option[String] = None,
  created: Instant,
  fonds: Option[String] = None,
  sync: Boolean = false,
  status: ImportDataset.Status.Value = ImportDataset.Status.Active,
  notes: Option[String] = None,
)

object ImportDataset {
  object Src extends Enumeration with StorableEnum {
    val Upload = Value("upload")
    val OaiPmh = Value("oaipmh")
    val Rs = Value("rs")

    implicit val _format: Format[ImportDataset.Src.Value] = EnumUtils.enumFormat(ImportDataset.Src)
  }

  object Status extends Enumeration with StorableEnum {
    val Active = Value("active")
    val OnHold = Value("onhold")
    val Inactive = Value("inactive")

    implicit val _format: Format[ImportDataset.Status.Value] = EnumUtils.enumFormat(ImportDataset.Status)
  }

  private implicit val config: Aux[Json.MacroOptions] = JsonConfiguration(optionHandlers = OptionHandlers.WritesNull)
  implicit val _format: Format[ImportDataset] = Json.format[ImportDataset]
}

case class ImportDatasetInfo(
  id: String,
  name: String,
  src: ImportDataset.Src.Value,
  contentType: Option[String] = None,
  fonds: Option[String] = None,
  sync: Boolean = false,
  status: ImportDataset.Status.Value = ImportDataset.Status.Active,
  notes: Option[String] = None,
)

object ImportDatasetInfo {
  implicit val _reads: Reads[ImportDatasetInfo] = Json.reads[ImportDatasetInfo]
}
