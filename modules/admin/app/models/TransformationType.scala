package models

import play.api.libs.json.Format
import utils.EnumUtils.enumFormat
import utils.db.StorableEnum

object TransformationType extends Enumeration with StorableEnum {
  val Xslt = Value("xslt")
  val XQuery = Value("xquery")
  val Json = Value("json")
  val Tabular = Value("tabular")

  /**
    * The content type of the output of a sequence of transformations, which
    * is that of the last one: XQuery and XSLT transformations produce XML.
    */
  def outputContentType(types: Seq[TransformationType.Value]): String = types.lastOption match {
    case Some(Json) => "application/json; charset=UTF-8"
    case Some(Tabular) => "text/csv; charset=UTF-8"
    case _ => "text/xml; charset=UTF-8"
  }

  implicit val _fmt: Format[TransformationType.Value] = enumFormat(TransformationType)
}
