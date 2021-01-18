package models

import java.util.regex.{Pattern, PatternSyntaxException}

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, optional}

case class ResourceSyncConfig(
  url: String,
  filter: Option[String] = None
)


object ResourceSyncConfig {
  final val URL = "url"
  final val FILTER = "filter"

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
    (__ \ FILTER).readNullable[String](Reads.filter(JsonValidationError("errors.badRegexPattern"))(isValidRegex))
  )(ResourceSyncConfig.apply _)

  implicit val _writes: Writes[ResourceSyncConfig] = Json.writes[ResourceSyncConfig]
  implicit val _format: Format[ResourceSyncConfig] = Format(_reads, _writes)

  val form: Form[ResourceSyncConfig] = Form(mapping(
    URL -> nonEmptyText.verifying("errors.invalidUrl", forms.isValidUrl),
    FILTER -> optional(nonEmptyText.verifying("errors.badRegexPattern", isValidRegex))
  )(ResourceSyncConfig.apply)(ResourceSyncConfig.unapply))
}
