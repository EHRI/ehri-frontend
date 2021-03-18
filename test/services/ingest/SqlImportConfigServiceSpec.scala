package services.ingest

import helpers._
import models.ImportConfig
import play.api.db.Database
import play.api.test.PlaySpecification

class SqlImportConfigServiceSpec extends SimpleAppTest with PlaySpecification {

  def service(implicit db: Database) = SqlImportConfigService(db, implicitApp.actorSystem)

  "Import config service" should {
    "locate items" in withDatabaseFixture("import-config-fixtures.sql") { implicit db =>
      val config = await(service.get("r1", "default"))
      config must beSome(ImportConfig(
        allowUpdates = true, properties = Some("r1-ead.properties"),
        defaultLang = Some("eng"), logMessage = "Testing"))
    }

    "delete items" in withDatabaseFixture("import-config-fixtures.sql") { implicit db =>
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in withDatabaseFixture("import-config-fixtures.sql") { implicit db =>
      val config = ImportConfig(defaultLang = Some("fra"), allowUpdates = true, logMessage = "Test 2")
      await(service.save("r1", "default", config)) must_== config
    }
  }
}
