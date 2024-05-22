package integration.admin

import helpers._
import mockdata.privilegedUser
import models.{EntityType, EntityTypeMetadata, FieldMetadata, Isdiah}
import play.api.libs.json.{JsNull, Json}
import play.api.test.FakeRequest


class EntityTypeMetadataApiSpec extends IntegrationTestRunner with ResourceUtils {

  private val entityTypeMetadataRoutes = controllers.datamodel.routes.EntityTypeMetadataApi

  "EntityTypeMetadataApi" should {

    "list entity types" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val r = FakeRequest(entityTypeMetadataRoutes.list())
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).as[Map[String, EntityTypeMetadata]].size must_== 2
    }

    "get entity type" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val r = FakeRequest(entityTypeMetadataRoutes.get(EntityType.Repository))
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).asOpt[EntityTypeMetadata] must beSome.which { c: EntityTypeMetadata =>
        c.name must_== "Repository Description"
      }
    }

    "save entity type metadata" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val data = Json.obj(
        "name" -> "Concept Description",
        "description" -> "A new entity type",
      )
      val r = FakeRequest(entityTypeMetadataRoutes.save(EntityType.Concept))
        .withUser(privilegedUser)
        .callWith(data)
      status(r) must_== OK
      contentAsJson(r).asOpt[EntityTypeMetadata] must beSome.which { c: EntityTypeMetadata =>
        c.name must_== "Concept Description"
      }
    }

    "list field metadata" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val r = FakeRequest(entityTypeMetadataRoutes.listFields())
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).as[Map[String, Seq[FieldMetadata]]].size must_== 2
    }

    "get field metadata with no data" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val r = FakeRequest(entityTypeMetadataRoutes.getField(EntityType.Repository, "no-such-field"))
        .withUser(privilegedUser)
        .call()
      status(r) must_== NOT_FOUND
      contentAsJson(r) must_== JsNull
    }

    "get field metadata" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val r = FakeRequest(entityTypeMetadataRoutes.getField(EntityType.Repository, Isdiah.HISTORY))
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).asOpt[FieldMetadata] must beSome.which { c: FieldMetadata =>
        c.name must_== "History"
      }
    }

    "create field metadata" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val data = Json.obj(
        "name" -> "General Context",
        "description" -> "Testing testing... 1, 2, 3...",
        "usage" -> FieldMetadata.Usage.Mandatory.toString,
        "seeAlso" -> Seq("https://example.com/new-field"),
      )
      val r = FakeRequest(entityTypeMetadataRoutes.saveField(EntityType.Repository, Isdiah.GEOCULTURAL_CONTEXT))
        .withUser(privilegedUser)
        .callWith(data)
      status(r) must_== OK // we don't distinguish between created and updated here
      contentAsJson(r).asOpt[FieldMetadata] must beSome.which { c: FieldMetadata =>
        c.description must beSome("Testing testing... 1, 2, 3...")
      }
    }

    "update field metadata" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val data = Json.obj(
        "name" -> "History",
        "description" -> "The history of the repository",
        "usage" -> FieldMetadata.Usage.Desirable.toString,
        "seeAlso" -> Seq("https://example.com/history"),
      )
      val r = FakeRequest(entityTypeMetadataRoutes.saveField(EntityType.Repository, Isdiah.HISTORY))
        .withUser(privilegedUser)
        .callWith(data)
      status(r) must_== OK
      contentAsJson(r).asOpt[FieldMetadata] must beSome.which { c: FieldMetadata =>
        c.usage must beSome(FieldMetadata.Usage.Desirable)
      }
    }

    "delete field metadata" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val r = FakeRequest(entityTypeMetadataRoutes.deleteField(EntityType.Repository, Isdiah.HISTORY))
        .withUser(privilegedUser)
        .call()
      status(r) must_== NO_CONTENT
    }

    "provide template info" in new ITestApp {
      val r = FakeRequest(entityTypeMetadataRoutes.templates())
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).as[Map[String, Map[String, Seq[String]]]].size must_== 7
    }
  }
}
