package services.ingest

import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json._
import services.data.ValidationError

// A result from the import endpoint
sealed trait IngestResult

object IngestResult {
  implicit val reads: Reads[IngestResult] = Reads { json =>
    json.validate[ValidationError]
      .map(e => ErrorLog("Validation error", e.toString))
      .orElse(json.validate[ErrorLog])
      .orElse(json.validate[ImportLog])
      .orElse(json.validate[SyncLog])
  }
  implicit val writes: Writes[IngestResult] = Writes {
    case i: ImportLog => Json.toJson(i)(ImportLog.format)
    case i: SyncLog => Json.toJson(i)(SyncLog.format)
    case i: ErrorLog => Json.toJson(i)(ErrorLog.format)
  }
  implicit val format: Format[IngestResult] = Format(reads, writes)
}

// The result of a regular import
case class ImportLog(
  createdKeys: Map[String, Seq[String]] = Map.empty,
  created: Int = 0,
  updatedKeys: Map[String, Seq[String]] = Map.empty,
  updated: Int = 0,
  unchangedKeys: Map[String, Seq[String]] = Map.empty,
  unchanged: Int = 0,
  message: Option[String] = None,
  event: Option[String] = None,
  errors: Map[String, String] = Map.empty,
) extends IngestResult {
  def hasDoneWork: Boolean = createdKeys.nonEmpty || updatedKeys.nonEmpty
}

object ImportLog {
  implicit val config: Aux[Json.MacroOptions] = JsonConfiguration(SnakeCase)
  implicit val format: Format[ImportLog] = Json.format[ImportLog]
}

// The result of an EAD synchronisation, which incorporates an import
case class SyncLog(
  deleted: Seq[String],
  created: Seq[String],
  moved: Map[String, String],
  log: ImportLog
) extends IngestResult

object SyncLog {
  implicit val format: Format[SyncLog] = Json.format[SyncLog]
}

// An import error we can understand, e.g. not a crash!
case class ErrorLog(error: String, details: String) extends IngestResult

object ErrorLog {
  implicit val format: Format[ErrorLog] = Json.format[ErrorLog]
}


