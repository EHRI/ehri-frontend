package services.datasets

import helpers._
import models.{ImportDataset, ImportDatasetInfo}
import org.postgresql.util.PSQLException
import play.api.Application

class SqlImportDatasetServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlImportDatasetService]

  "Dataset service" should {
    "locate items" in new DBTestApp("data-transformation-fixtures.sql") {
      val ds = await(service.get("r1", "default"))
      ds.name must_== "Default"
    }

    "list items" in new DBTestApp("data-transformation-fixtures.sql") {
      await(service.list("r1")).size must_== 1
    }

    "list all items" in new DBTestApp("data-transformation-fixtures.sql") {
      await(service.listAll()).size must_== 2
    }

    "delete items" in new DBTestApp("data-transformation-fixtures.sql") {
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in new DBTestApp("data-transformation-fixtures.sql") {
      val ds = await(service.create("r1", ImportDatasetInfo("new", "New DS", ImportDataset.Src.Upload)))
      ds.name must_== "New DS"
    }

    "update items" in new DBTestApp("data-transformation-fixtures.sql") {
      val ds = await(service.update("r1", "default", ImportDatasetInfo("default", "Updated DS", ImportDataset.Src.Upload)))
      ds.name must_== "Updated DS"
    }

    "enforce id pattern" in new DBTestApp("data-transformation-fixtures.sql") {
      await(service.create("r1", ImportDatasetInfo("foo bar", "New DS", ImportDataset.Src.Upload))) must throwA[PSQLException].like {
        case e => e.getMessage must contain("import_dataset_id_pattern")
      }
    }

    "import items" in new DBTestApp("data-transformation-fixtures.sql") {
      val data = Seq(
        ImportDatasetInfo("default", "Updated DS", ImportDataset.Src.Rs),
        ImportDatasetInfo("new", "New DS", ImportDataset.Src.Upload)
      )
      val count = await(service.batch("r1", data))
      count.size must_== 2
    }
  }
}
