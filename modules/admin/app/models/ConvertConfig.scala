package models

import play.api.libs.json.{Format, JsObject, Json, Reads}


sealed trait ConvertConfig {
  def force: Boolean
}

object ConvertConfig {
  implicit val _reads: Reads[ConvertConfig] = Reads { json =>
    json.validate[TransformationList].orElse(json.validate[ConvertSpec])
  }
}

case class TransformationList(
 mappings: Seq[(String, JsObject)],
 force: Boolean = false
) extends ConvertConfig

object TransformationList {
  implicit val _format: Format[TransformationList] = Json.format[TransformationList]
}

case class ConvertSpec(
  mappings: Seq[(TransformationType.Value, String, JsObject)],
  force: Boolean = false
) extends ConvertConfig

object ConvertSpec {
  implicit val _format: Format[ConvertSpec] = Json.format[ConvertSpec]
}
