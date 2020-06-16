package services.harvesting

import akka.actor.ActorSystem
import helpers._
import models.OaiPmhConfig
import org.h2.jdbc.JdbcSQLException
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification

class SqlOaiPmhConfigServiceSpec extends PlaySpecification {

  private val actorSystem = new GuiceApplicationBuilder().build().injector.instanceOf[ActorSystem]

  def service(implicit db: Database) = SqlOaiPmhConfigService(db, actorSystem)

  "OAI-PMH config service" should {
    "locate items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      val config = await(service.get("r1"))
      config must beSome
    }

    "delete items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      await(service.delete("r1")) must_== true
    }

    "create items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      val config = OaiPmhConfig("https://test.com/testing", "oai_dc")
      // TODO: fix this so it works with H2 for testing
      // in the meantime validate it throws an exception
      await(service.save("r1", config)) must throwA[JdbcSQLException]
    }
  }
}
