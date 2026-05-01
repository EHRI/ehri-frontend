package services.harvesting

import helpers._
import models.ResourceSyncConfig
import org.postgresql.util.PSQLException
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

    "validate items" in new DBTestApp("resourcesync-config-fixtures.sql") {
      val config = ResourceSyncConfig("https://test.com/testing", delay = Some(-50))
      await(service.save("r1", "invalid", config)) must throwA[PSQLException].like {
        case e => e.getMessage must contain("violates check constraint \"resourcesync_config_delay_check\"")
      }
    }
  }
}
