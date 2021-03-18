package services.harvesting

import helpers._
import models.OaiPmhConfig
import play.api.db.Database
import play.api.test.PlaySpecification

class SqlOaiPmhConfigServiceSpec extends SimpleAppTest with PlaySpecification {

  def service(implicit db: Database) = SqlOaiPmhConfigService(db, implicitApp.actorSystem)

  "OAI-PMH config service" should {
    "locate items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      val config = await(service.get("r1", "default"))
      config must beSome
    }

    "delete items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      val config = OaiPmhConfig("https://test.com/testing", "oai_dc")
      await(service.save("r1", "default", config)) must_== config
    }
  }
}
