package integration.admin

import helpers._
import mockdata.privilegedUser
import models.{EntityType, FieldMetadata, Isdiah}
import play.api.libs.json.{JsNull, Json}
import play.api.test.FakeRequest


class FieldMetadataApiSpec extends IntegrationTestRunner with ResourceUtils {

  private val fieldMetadataRoutes = controllers.fieldmeta.routes.FieldMetadataApi

  "Field Metadata API" should {

    "list field metadata" in new DBTestApp("field-metadata-fixtures.sql") {
      val r = FakeRequest(fieldMetadataRoutes.list())
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).as[Map[String, Seq[FieldMetadata]]].size must_== 2
    }

    "get field metadata with no data" in new DBTestApp("field-metadata-fixtures.sql") {
      val r = FakeRequest(fieldMetadataRoutes.get(EntityType.RepositoryDescription, "no-such-field"))
        .withUser(privilegedUser)
        .call()
      status(r) must_== NOT_FOUND
      contentAsJson(r) must_== JsNull
    }

    "get field metadata" in new DBTestApp("field-metadata-fixtures.sql") {
      val r = FakeRequest(fieldMetadataRoutes.get(EntityType.RepositoryDescription, Isdiah.HISTORY))
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).asOpt[FieldMetadata] must beSome.which { c: FieldMetadata =>
        c.name must_== "History"
      }
    }

    "create field metadata" in new DBTestApp("field-metadata-fixtures.sql") {
      val data = Json.obj(
        "name" -> "General Context",
        "description" -> "Testing testing... 1, 2, 3...",
        "usage" -> FieldMetadata.Usage.Mandatory.toString,
        "seeAlso" -> Seq("https://example.com/new-field"),
      )
      val r = FakeRequest(fieldMetadataRoutes.save(EntityType.RepositoryDescription, Isdiah.GEOCULTURAL_CONTEXT))
        .withUser(privilegedUser)
        .callWith(data)
      status(r) must_== OK // we don't distinguish between created and updated here
      contentAsJson(r).asOpt[FieldMetadata] must beSome.which { c: FieldMetadata =>
        c.description must beSome("Testing testing... 1, 2, 3...")
      }
    }

    "update field metadata" in new DBTestApp("field-metadata-fixtures.sql") {
      val data = Json.obj(
        "name" -> "History",
        "description" -> "The history of the repository",
        "usage" -> FieldMetadata.Usage.Desirable.toString,
        "seeAlso" -> Seq("https://example.com/history"),
      )
      val r = FakeRequest(fieldMetadataRoutes.save(EntityType.RepositoryDescription, Isdiah.HISTORY))
        .withUser(privilegedUser)
        .callWith(data)
      status(r) must_== OK
      contentAsJson(r).asOpt[FieldMetadata] must beSome.which { c: FieldMetadata =>
        c.usage must beSome(FieldMetadata.Usage.Desirable)
      }
    }

    "delete field metadata" in new DBTestApp("field-metadata-fixtures.sql") {
      val r = FakeRequest(fieldMetadataRoutes.delete(EntityType.RepositoryDescription, Isdiah.HISTORY))
        .withUser(privilegedUser)
        .call()
      status(r) must_== NO_CONTENT
    }

    "provide template info" in new ITestApp {
      val r = FakeRequest(fieldMetadataRoutes.templates())
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
    }
  }
}
