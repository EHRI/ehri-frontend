package services.fieldmeta

import anorm.AnormException
import helpers.IntegrationTestRunner
import models.{EntityType, FieldMetadata}
import play.api.Application


class SqlFieldMetadataServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlFieldMetadataService]

  "FieldMeta service" should {
    "locate items" in new DBTestApp("field-metadata-fixtures.sql") {
      val ds = await(service.list())
      ds.size must_== 2
    }

    "locate items by entity type" in new DBTestApp("field-metadata-fixtures.sql") {
      val ds = await(service.list(Some(EntityType.RepositoryDescription)))
      ds.size must_== 1
    }

    "create items" in new DBTestApp("field-metadata-fixtures.sql") {
      val ds = await(service.create(FieldMetadata(
        EntityType.RepositoryDescription,
        "new",
        "New Field",
        Some("New Field Description"),
        Some(FieldMetadata.Usage.Mandatory),
        Some("New Field Category"),
        Seq("New Field See Other"),
      )))
      ds.name must_== "New Field"
      ds.seeOther must_== Seq("New Field See Other")
      ds.usage must beSome(FieldMetadata.Usage.Mandatory)
      ds.created must beSome
      ds.updated must beNone
    }

    "update items" in new DBTestApp("field-metadata-fixtures.sql") {
      val ds = await(service.update(FieldMetadata(
        EntityType.RepositoryDescription,
        "history",
        "Updated Field",
        Some("Updated Field Description"),
        Some(FieldMetadata.Usage.Desirable),
        Some("Updated Field Category"),
        Seq("Updated Field See Other"),
      )))
      ds.name must_== "Updated Field"
      ds.seeOther must_== Seq("Updated Field See Other")
      ds.usage must beSome(FieldMetadata.Usage.Desirable)
      ds.created must beSome
      ds.updated must beSome
    }

    "update non-existing items" in new DBTestApp("field-metadata-fixtures.sql") {
      await(service.update(FieldMetadata(
        EntityType.RepositoryDescription,
        "non-existing",
        "Updated Field",
        Some("Updated Field Description"),
        Some(FieldMetadata.Usage.Desirable),
        Some("Updated Field Category"),
        Seq("Updated Field See Other"),
      ))) must throwA[AnormException].like {
        case e => e.getMessage must contain("No rows when expecting a single one")
      }
    }

    "delete items" in new DBTestApp("field-metadata-fixtures.sql") {
      await(service.delete(EntityType.RepositoryDescription, "history")) must_== true
      await(service.list()).size must_== 1
      await(service.list(Some(EntityType.RepositoryDescription))).size must_== 0
    }
  }
}
