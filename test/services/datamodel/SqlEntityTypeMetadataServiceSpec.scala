package services.datamodel

import helpers.IntegrationTestRunner
import models.{EntityType, EntityTypeMetadata, EntityTypeMetadataInfo, FieldMetadata, FieldMetadataInfo}
import play.api.Application


class SqlEntityTypeMetadataServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlEntityTypeMetadataService]

  "EntityTypeMetadataService" should {
    "locate entity types" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.list())
      ds.size must_== 2
    }

    "locate entity types by name" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.list())
      ds.size must_== 2
    }

    "get entity type" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.get(EntityType.DocumentaryUnit))
      ds must beSome.which { (d:EntityTypeMetadata) =>
        d.name must_== "Documentary Unit Description"
        d.description must beSome("A description of a documentary unit.")
        d.updated must beNone
      }
    }

    "save entity type" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.save(EntityType.Repository, EntityTypeMetadataInfo(
        "Repository Description",
        Some("Updated Entity Type Description")
      )))
      ds.name must_== "Repository Description"
      ds.description must beSome("Updated Entity Type Description")
      ds.created must beSome
      ds.updated must beSome // already exists
    }

    "locate fields" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.list())
      ds.size must_== 2
    }

    "locate fields by entity type" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.listFields(Some(EntityType.Repository)))
      ds.size must_== 1
    }

    "get fields" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.getField(EntityType.DocumentaryUnit, "locationOfOriginals"))
      ds must beSome.which { (d:FieldMetadata) =>
        d.name must_== "Location of Originals"
        d.seeAlso must_== Seq(
          "https://eadiva.com/originalsloc/",
          "https://www.loc.gov/ead/tglib/elements/originalsloc.html"
        )
      }
    }

    "save fields" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.saveField(EntityType.Repository, "new", FieldMetadataInfo(
        "New Field",
        Some("New Field Description"),
        Some(FieldMetadata.Usage.Mandatory),
        Some("New Field Category"),
        None,
        Seq("New Field See Other", "https://example.com/new-field"),
      )))
      ds.name must_== "New Field"
      ds.usage must beSome(FieldMetadata.Usage.Mandatory)
      ds.seeAlso must_== Seq("New Field See Other", "https://example.com/new-field")
      ds.created must beSome
      ds.updated must beNone
    }

    "save existing fields" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val ds = await(service.saveField(EntityType.Repository, "history", FieldMetadataInfo(
        "Updated Field",
        Some("Updated Field Description"),
        Some(FieldMetadata.Usage.Desirable),
        Some("Updated Field Category"),
        None,
        Seq("Updated Field See Other")
      )))
      ds.name must_== "Updated Field"
      ds.seeAlso must_== Seq("Updated Field See Other")
      ds.usage must beSome(FieldMetadata.Usage.Desirable)
      ds.created must beSome
      ds.updated must beSome
    }

    "save fields with a variety of different array values" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      val info = FieldMetadataInfo(
        "New Field",
        Some("New Field Description"),
        Some(FieldMetadata.Usage.Mandatory),
        Some("New Field Category"),
        None,
        Seq("New Field See Other", "https://example.com/new-field"),
      )
      val ds = await(service.saveField(EntityType.Repository, "new", info))
      ds.seeAlso must_== Seq("New Field See Other", "https://example.com/new-field")
      val ds2 = await(service.saveField(EntityType.Repository, "new2", info.copy(seeAlso = Seq())))
      ds2.seeAlso must_== Seq()
    }

    "delete fields" in new DBTestApp("entity-type-metadata-fixtures.sql") {
      await(service.deleteField(EntityType.Repository, "history")) must_== true
      await(service.listFields()).size must_== 1
      await(service.listFields(Some(EntityType.Repository))).size must_== 0
    }
  }
}
