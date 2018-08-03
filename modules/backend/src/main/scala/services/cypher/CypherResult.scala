package services.cypher

import play.api.libs.json._
import utils.CsvHelpers


case class CypherResult(columns: Seq[String], data: Seq[List[JsValue]]) {
  /**
    * Convert Cypher JSON results to CSV, with nested arrays pipe-delimited.
    */
  def toCsv(sep: Char = ',', quote: Boolean = false): String =
    CsvHelpers.writeCsv(columns, data
      .map(_.collect(CypherResult.jsToString).toArray), sep = sep)

  def toData: Seq[Seq[String]] = data.map(_.collect(CypherResult.jsToString))
}

object CypherResult {
  implicit val _format: Format[CypherResult] = Json.format[CypherResult]

  def jsToString: PartialFunction[JsValue, String] = {
    case JsString(s) => s
    case JsNumber(i) => i.toString()
    case JsNull => ""
    case JsBoolean(b) => b.toString
    case list: JsArray => list.value.map(jsToString).mkString("|")
  }
}

