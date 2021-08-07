package models

import play.api.libs.functional.syntax._
import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json._
import services.data.ErrorSet

// A result from the import endpoint
sealed trait IngestResult

object IngestResult {

  implicit val reads: Reads[IngestResult] = Reads { json =>
    json.validate[ImportValidationError]
      .map(e => ErrorLog(s"Validation error at ${e.context}", e.toString))
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

case class ImportValidationError(context: String, errorSet: ErrorSet) extends RuntimeException(errorSet.toString) {
  override def toString: String = errorSet.toString
}

object ImportValidationError {
  implicit val _reads: Reads[ImportValidationError] = (
    (__ \ "error").read[String] and
    (__ \ "context").read[String] and
    (__ \ "details").read[ErrorSet]
  )((_, c, s) => ImportValidationError(c, s))
}


// The result of a regular import
case class ImportLog(
  createdKeys: Map[String, Seq[String]] = Map.empty,
  updatedKeys: Map[String, Seq[String]] = Map.empty,
  unchangedKeys: Map[String, Seq[String]] = Map.empty,
  message: Option[String] = None,
  event: Option[String] = None,
  errors: Map[String, String] = Map.empty,
) extends IngestResult {
  def hasDoneWork: Boolean = createdKeys.nonEmpty || updatedKeys.nonEmpty
  def created: Int = createdKeys.map(_._2.size).sum
  def updated: Int = updatedKeys.map(_._2.size).sum
  def unchanged: Int = unchangedKeys.map(_._2.size).sum
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


