package models

import java.time.Instant


case class OaiPmhConfig(
  url: String,
  format: String,
  set: Option[String] = None,
  from: Option[Instant] = None,
  until: Option[Instant] = None,
  auth: Option[BasicAuthConfig] = None
) extends HarvestConfig {
  override val src: ImportDataset.Src.Value = ImportDataset.Src.OaiPmh
}

object OaiPmhConfig {
  final val URL = "url"
  final val METADATA_FORMAT = "format"
  final val SET = "set"
  final val FROM = "from"
  final val UNTIL = "until"
  final val AUTH = "auth"

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val _reads: Reads[OaiPmhConfig] = (
    (__ \ URL).read[String](Reads.filter(JsonValidationError("errors.invalidUrl"))(forms.isValidUrl)) and
      (__ \ METADATA_FORMAT).read[String] and
      (__ \ SET).readNullable[String] and
      (__ \ FROM).readNullable[Instant] and
      (__ \ UNTIL).readNullable[Instant] and
      (__ \ AUTH).readNullable[BasicAuthConfig]
    ) (OaiPmhConfig.apply _)

  implicit val _writes: Writes[OaiPmhConfig] = Json.writes[OaiPmhConfig]
  implicit val _format: Format[OaiPmhConfig] = Format(_reads, _writes)
}
