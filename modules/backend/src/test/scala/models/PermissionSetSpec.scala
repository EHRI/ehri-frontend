package models

import org.specs2.mutable.Specification
import play.api.libs.json.Json


class PermissionSetSpec extends Specification {

  "GlobalPermissionSet" should {
    "parse correctly" in {
      val data: List[Map[String, Map[String, List[String]]]] = List(
        Map(
          "bob" -> Map(
            ContentTypes.DocumentaryUnit.toString -> List(
              PermissionType.Create.toString,
              PermissionType.Update.toString,
              "invalidPerm"
            ),
            "invalidEntity" -> List()
          )
        )
      )

      val permSet = Json.toJson(data).as[GlobalPermissionSet]
      permSet.has(ContentTypes.DocumentaryUnit, PermissionType.Create) must beTrue
      permSet.has(ContentTypes.DocumentaryUnit, PermissionType.Update) must beTrue
      permSet.has(ContentTypes.DocumentaryUnit, PermissionType.Annotate) must beFalse
    }
  }

  "ItemPermissionSet" should {
    "parse correctly" in {
      val data: List[Map[String, List[String]]] = List(
        Map(
          "bob" -> List(
            PermissionType.Create.toString,
            PermissionType.Update.toString,
            "invalidPerm"
          )
        )
      )

      val permSet = Json.toJson(data).as(ItemPermissionSet.restReads(ContentTypes.DocumentaryUnit))
      permSet.has(PermissionType.Create) must beTrue
      permSet.has(PermissionType.Update) must beTrue
      permSet.has(PermissionType.Annotate) must beFalse
    }
  }
}
