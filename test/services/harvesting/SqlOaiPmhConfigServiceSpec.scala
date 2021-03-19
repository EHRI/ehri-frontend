package services.harvesting

import helpers._
import models.OaiPmhConfig
import play.api.Application

class SqlOaiPmhConfigServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlOaiPmhConfigService]

  "OAI-PMH config service" should {
    "locate items" in new DBTestApp("oaipmh-config-fixtures.sql") {
      val config = await(service.get("r1", "default"))
      config must beSome
    }

    "delete items" in new DBTestApp("oaipmh-config-fixtures.sql") {
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in new DBTestApp("oaipmh-config-fixtures.sql") {
      val config = OaiPmhConfig("https://test.com/testing", "oai_dc")
      await(service.save("r1", "default", config)) must_== config
    }
  }
}
