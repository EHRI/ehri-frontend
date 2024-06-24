package services.fieldmeta

import anorm.AnormException
import helpers.IntegrationTestRunner
import models.{EntityType, FieldMetadata, FieldMetadataInfo}
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

    "get items" in new DBTestApp("field-metadata-fixtures.sql") {
      val ds = await(service.get(EntityType.RepositoryDescription, "history"))
      ds must beSome.which {_.name must_== "History"}
    }

    "save items" in new DBTestApp("field-metadata-fixtures.sql") {
      val ds = await(service.save(EntityType.RepositoryDescription, "new", FieldMetadataInfo(
        "New Field",
        Some("New Field Description"),
        Some(FieldMetadata.Usage.Mandatory),
        Some("New Field Category"),
        Seq("New Field See Other"),
      )))
      ds.name must_== "New Field"
      ds.seeAlso must_== Seq("New Field See Other")
      ds.usage must beSome(FieldMetadata.Usage.Mandatory)
      ds.created must beSome
      ds.updated must beNone
    }

    "save existing items" in new DBTestApp("field-metadata-fixtures.sql") {
      val ds = await(service.save(EntityType.RepositoryDescription, "history", FieldMetadataInfo(
        "Updated Field",
        Some("Updated Field Description"),
        Some(FieldMetadata.Usage.Desirable),
        Some("Updated Field Category"),
        Seq("Updated Field See Other")
      )))
      ds.name must_== "Updated Field"
      ds.seeAlso must_== Seq("Updated Field See Other")
      ds.usage must beSome(FieldMetadata.Usage.Desirable)
      ds.created must beSome
      ds.updated must beSome
    }

    "delete items" in new DBTestApp("field-metadata-fixtures.sql") {
      await(service.delete(EntityType.RepositoryDescription, "history")) must_== true
      await(service.list()).size must_== 1
      await(service.list(Some(EntityType.RepositoryDescription))).size must_== 0
    }
  }
}
