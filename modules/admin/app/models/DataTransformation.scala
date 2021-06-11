package models

import java.time.Instant

import play.api.libs.json.{Format, Json}
import utils.EnumUtils._
import utils.db.StorableEnum


object DataTransformation {
  object TransformationType extends Enumeration with StorableEnum {
    val Xslt = Value("xslt")
    val XQuery = Value("xquery")

    implicit val _fmt: Format[TransformationType.Value] = enumFormat(TransformationType)
  }

  implicit val _fmt: Format[DataTransformation] = Json.format[DataTransformation]
}

case class DataTransformation(
  id: String,
  name: String,
  repoId: Option[String],
  bodyType: DataTransformation.TransformationType.Value,
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
  bodyType: DataTransformation.TransformationType.Value,
  body: String,
  comments: String,
  hasParams: Boolean = false,
)
