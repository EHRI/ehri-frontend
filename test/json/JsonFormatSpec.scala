package test.json

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.Messages
import play.api.libs.json.Json
import models.json.TestDocumentaryUnit

class JsonFormatSpec extends Specification {

  "Documentary Unit Format should read and write with no changes" in {
    import models.json._
    import models.json.DocumentaryUnitFormat._

    //val validation = Json.parse(json.documentaryUnit).validate[TestDocumentaryUnit]

    val data =
      """
        {
          "id": "id-1",
          "data": {
            "foo": "teststr1",
            "bar": "teststr2"
          }
        }
      """

    val validation = Json.parse(data).validate[TestData]
    validation.asEither must beRight
    println(validation.get)
    println(Json.toJson(validation.get))

    println(Json.toJson(validation.get)(testCaseWrites))

    //validation.asEither must beRight
    //val doc = validation.get
    /*println("DOC: " + doc)
    doc.descriptions.map { d =>
      println("DESC: " + d)
      println("DESC JSON: " + Json.toJson(d))
    }
    println("JSON: " + Json.toJson(doc))

    Json.toJson(doc).as[TestDocumentaryUnit] must beEqualTo(doc)*/
  }

}