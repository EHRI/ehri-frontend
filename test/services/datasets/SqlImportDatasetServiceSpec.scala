package services.datasets

import akka.actor.ActorSystem
import helpers._
import models.{ImportDataset, ImportDatasetInfo}
import org.postgresql.util.PSQLException
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification

class SqlImportDatasetServiceSpec extends PlaySpecification {

  private val actorSystem = new GuiceApplicationBuilder().build().injector.instanceOf[ActorSystem]

  def service(implicit db: Database) = SqlImportDatasetService(db, actorSystem)

  "Dataset service" should {
    "locate items" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      val ds = await(service.get("r1", "default"))
      ds.name must_== "Default"
    }

    "list items" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      await(service.list("r1")).size must_== 1
    }

    "delete items" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      val ds = await(service.create("r1", ImportDatasetInfo("new", "New DS", ImportDataset.Src.Upload)))
      ds.name must_== "New DS"
    }

    "enforce id pattern" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      await(service.create("r1", ImportDatasetInfo("foo bar", "New DS", ImportDataset.Src.Upload))) must throwA[PSQLException].like {
        case e => e.getMessage must contain("import_dataset_id_pattern")
      }
    }

    "enforce item_id pattern" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      await(service.create("r1", ImportDatasetInfo("foo_bar", "New DS", ImportDataset.Src.Upload, fonds = Some("nope")))) must throwA[PSQLException].like {
        case e => e.getMessage must contain("import_dataset_item_id_pattern")
      }
      val ds = await(service.create("r1", ImportDatasetInfo("foo_bar", "New DS", ImportDataset.Src.Upload, fonds = Some("r1-1"))))
      ds.fonds must beSome("r1-1")
    }
  }
}
