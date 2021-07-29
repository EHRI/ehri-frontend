package models

import play.api.libs.json.{Format, Json}

import java.time.Instant


object DataTransformation {
  implicit val _fmt: Format[DataTransformation] = Json.format[DataTransformation]
}

case class DataTransformation(
  id: String,
  name: String,
  repoId: Option[String],
  bodyType: TransformationType.Value,
  body: String,
  created: Instant,
  comments: String,
  hasParams: Boolean = false,
)

object DataTransformationInfo {
  implicit val _fmt: Format[DataTransformationInfo] = Json.format[DataTransformationInfo]
}

case class DataTransformationInfo(
  name: String,
  bodyType: TransformationType.Value,
  body: String,
  comments: String,
  hasParams: Boolean = false,
)
