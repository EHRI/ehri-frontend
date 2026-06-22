package services.data

import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.{Format, Json, JsonConfiguration}

/**
  * Results from a backend batch operation.
  *
  * FIXME: there is redundency between this class and the
  * `IngestResult` class in the admin package that should
  * be refactored out.
  */
case class BatchResult(
  createdKeys: Map[String, Seq[String]] = Map.empty,
  updatedKeys: Map[String, Seq[String]] = Map.empty,
  unchangedKeys: Map[String, Seq[String]] = Map.empty,
  errors: Map[String, String]
) {
  def hasDoneWork: Boolean = createdKeys.nonEmpty || updatedKeys.nonEmpty
  def updated: Int = updatedKeys.map(_._2.size).sum
  def created: Int = createdKeys.map(_._2.size).sum
  def unchanged: Int = unchangedKeys.map(_._2.size).sum
}

object BatchResult {
  implicit val config: Aux[Json.MacroOptions] = JsonConfiguration(SnakeCase)
  implicit val _format: Format[BatchResult] = Json.format[BatchResult]
}
