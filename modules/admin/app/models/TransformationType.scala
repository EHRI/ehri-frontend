package models

import play.api.libs.json.Format
import utils.EnumUtils.enumFormat
import utils.db.StorableEnum

object TransformationType extends Enumeration with StorableEnum {
  val Xslt = Value("xslt")
  val XQuery = Value("xquery")
  val Json = Value("json")
  val Tabular = Value("tabular")

  implicit val _fmt: Format[TransformationType.Value] = enumFormat(TransformationType)
}
