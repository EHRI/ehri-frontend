package models

import java.util.regex.{Pattern, PatternSyntaxException}

case class ResourceSyncConfig(
  url: String,
  filter: Option[String] = None,
  auth: Option[BasicAuthConfig] = None
) extends HarvestConfig {
  override val src: ImportDataset.Src.Value = ImportDataset.Src.Rs
}

object ResourceSyncConfig {
  final val URL = "url"
  final val FILTER = "filter"
  final val AUTH = "auth"

  private val isValidRegex: (String => Boolean) = s => try {
    Pattern.compile(s);
    true
  } catch {
    case _: PatternSyntaxException => false
  }

  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  implicit val _reads: Reads[ResourceSyncConfig] = (
    (__ \ URL).read(Reads.filter(JsonValidationError("errors.invalidUrl"))(forms.isValidUrl)) and
    (__ \ FILTER).readNullable[String](Reads.filter(JsonValidationError("errors.badRegexPattern"))(isValidRegex)) and
    (__ \ AUTH).readNullable[BasicAuthConfig]
  )(ResourceSyncConfig.apply _)

  implicit val _writes: Writes[ResourceSyncConfig] = Json.writes[ResourceSyncConfig]
  implicit val _format: Format[ResourceSyncConfig] = Format(_reads, _writes)
}
