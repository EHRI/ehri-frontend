package models

import play.api.libs.json.{Format, Json, Reads}

sealed trait ConvertConfig

object ConvertConfig {
  implicit val _reads: Reads[ConvertConfig] = Reads { json =>
    json.validate[TransformationList].orElse(json.validate[ConvertSpec])
  }
}

case class TransformationList(
  mappings: Seq[String]
) extends ConvertConfig

object TransformationList {
  implicit val _format: Format[TransformationList] = Json.format[TransformationList]
}

case class ConvertSpec(
  mappings: Seq[(DataTransformation.TransformationType.Value, String)]
) extends ConvertConfig

object ConvertSpec {
  implicit val _format: Format[ConvertSpec] = Json.format[ConvertSpec]
}
