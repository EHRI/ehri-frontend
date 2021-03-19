package services.ingest

import helpers._
import models.ImportConfig
import play.api.Application

class SqlImportConfigServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlImportConfigService]

  "Import config service" should {
    "locate items" in new DBTestApp("import-config-fixtures.sql") {
      val config = await(service.get("r1", "default"))
      config must beSome(ImportConfig(
        allowUpdates = true, properties = Some("r1-ead.properties"),
        defaultLang = Some("eng"), logMessage = "Testing"))
    }

    "delete items" in new DBTestApp("import-config-fixtures.sql") {
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in new DBTestApp("import-config-fixtures.sql") {
      val config = ImportConfig(defaultLang = Some("fra"), allowUpdates = true, logMessage = "Test 2")
      await(service.save("r1", "default", config)) must_== config
    }
  }
}
