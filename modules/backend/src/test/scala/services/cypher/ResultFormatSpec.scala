package services.cypher

import play.api.libs.json.{JsString, Json}
import play.api.test.PlaySpecification

class ResultFormatSpec extends PlaySpecification {
  "Cypher result set" should {
    "convert to CSV with nested arrays pipe delimited" in {
      val rs = ResultFormat(Seq("col1", "col2"),
          Seq(Seq(JsString("val1"), Json.arr(1, 2))))
      rs.toCsv() must_== "col1,col2\nval1,1|2\n"
    }
  }
}
