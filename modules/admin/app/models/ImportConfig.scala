package models

import play.api.libs.json.{Format, Json}

case class ImportConfig(
  allowUpdates: Boolean = false,
  useSourceId: Boolean = false,
  tolerant: Boolean = false,
  properties: Option[String] = None,
  defaultLang: Option[String] = None,
  logMessage: String,
  batchSize: Option[Int] = None,
  comments: Option[String] = None,
)

object ImportConfig {
  implicit val _format: Format[ImportConfig] = Json.format[ImportConfig]
}
