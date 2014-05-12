package models.json

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.test.PlaySpecification

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class JsPathExtensionsSpec extends PlaySpecification {

  "JsPath with extensions" should {
    "allow performing reads with value equality constraints" in {
      case class TestData(field1: String, field2: String)
      val testValidJson: JsValue = Json.obj(
        "field1" -> "good",
        "field2" -> "anotherval"
      )
      val testInvalidJson: JsValue = Json.obj(
        "field1" -> "bad",
        "field2" -> "anotherval"
      )
      val testJsonReads: Reads[TestData] = (
        (__ \ "field1").readIfEquals("good") and
        (__ \ "field2").read[String]
      )(TestData.apply _)
      testValidJson.validate(testJsonReads).asOpt must beSome.which { value =>
        value must equalTo(TestData("good", "anotherval"))
      }
      testInvalidJson.validate(testJsonReads).asEither must beLeft
    }

    "allow reading nullable lists" in {
      case class TestData(field1: String, field2: List[String])
      // valid
      val testJson1: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr("foo")
      )
      // valid - list will be empty
      val testJson2: JsValue = Json.obj(
        "field1" -> "val"
      )
      // invalid, list exists but is wrong type
      val testJson3: JsValue = Json.obj(
        "field1" -> "val",
        "field2" -> Json.arr(
          Json.obj("foo" -> "bar")
        )
      )
      val testJsonReads: Reads[TestData] = (
        (__ \ "field1").read[String] and
        (__ \ "field2").nullableListReads[String]
      )(TestData.apply _)

      testJson1.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2.headOption must beSome.which { value =>
          value must equalTo("foo")
        }
      }
      testJson2.validate(testJsonReads).asOpt must beSome.which { data =>
        data.field2 must beEmpty
      }
      testJson3.validate(testJsonReads).asEither must beLeft
    }

    "allow writing nullable lists" in {
      case class TestData(field1: String, field2: List[String])
      val item1 = TestData("foo", List("bar"))
      val item2 = TestData("foo", List.empty)

      val testJsonWrites: Writes[TestData] = (
        (__ \ "field1").write[String] and
        (__ \ "field2").nullableListWrites[String]
      )(unlift(TestData.unapply))

      Json.toJson(item1)(testJsonWrites) must equalTo(Json.obj(
        "field1" -> "foo",
        "field2" -> Json.arr("bar")
      ))

      Json.toJson(item2)(testJsonWrites) must equalTo(Json.obj(
        "field1" -> "foo"
      ))
    }
  }
}
