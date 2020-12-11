package services.harvesting

import akka.actor.ActorSystem
import helpers._
import models.ResourceSyncConfig
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification

class SqlResourceSyncConfigServiceSpec extends PlaySpecification {

  private val actorSystem = new GuiceApplicationBuilder().build().injector.instanceOf[ActorSystem]

  def service(implicit db: Database) = SqlResourceSyncConfigService(db, actorSystem)

  "ResourceSync config service" should {
    "locate items" in withDatabaseFixture("resourcesync-config-fixtures.sql") { implicit db =>
      val config = await(service.get("r1", "default"))
      config must beSome
    }

    "delete items" in withDatabaseFixture("resourcesync-config-fixtures.sql") { implicit db =>
      await(service.delete("r1", "default")) must_== true
    }

    "create items" in withDatabaseFixture("resourcesync-config-fixtures.sql") { implicit db =>
      val config = ResourceSyncConfig("https://test.com/testing")
      await(service.save("r1", "default", config)) must_== config
    }
  }
}
