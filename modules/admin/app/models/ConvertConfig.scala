package models

import defines.FileStage
import play.api.libs.json.{Format, Json}

case class ConvertConfig(
  src: Seq[FileStage.Value],
)

object ConvertConfig {
  implicit val _format: Format[ConvertConfig] = Json.format[ConvertConfig]
}


