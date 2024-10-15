package services.data

import play.api.libs.json.{Format, Json}

case class BatchResult(created: Int, updated: Int, unchanged: Int, errors: Map[String, String])

object BatchResult {
  implicit val _format: Format[BatchResult] = Json.format[BatchResult]
}
