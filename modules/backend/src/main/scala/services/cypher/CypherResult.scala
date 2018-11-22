package services.cypher

import play.api.libs.json._


case class CypherResult(columns: Seq[String], data: Seq[List[JsValue]]) {
  def toData: Seq[Seq[String]] = data.map(_.collect(CypherResult.jsToString))
}

object CypherResult {
  implicit val _format: Format[CypherResult] = Json.format[CypherResult]

  def jsToString: PartialFunction[JsValue, String] = {
    case JsString(s) => s
    case JsNumber(i) => i.toString
    case JsBoolean(b) => b.toString
    case JsNull => ""
    case list: JsArray => list.value.map(jsToString).mkString("|")
  }
}

