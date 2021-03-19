package services.harvesting

import helpers._
import models.ResourceSyncConfig
import play.api.Application

class SqlResourceSyncConfigServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlResourceSyncConfigService]

  "ResourceSync config service" should {
    "locate items" in new DBTestApp("resourcesync-config-fixtures.sql") {
      val config = await(service.get("r1", "default"))
      config must beSome
    }

    "delete items" in new DBTestApp("resourcesync-config-fixtures.sql") {
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in new DBTestApp("resourcesync-config-fixtures.sql") {
      val config = ResourceSyncConfig("https://test.com/testing")
      await(service.save("r1", "default", config)) must_== config
    }
  }
}
