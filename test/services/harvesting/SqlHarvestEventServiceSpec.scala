package services.harvesting

import akka.actor.ActorSystem
import helpers._
import mockdata.adminUserProfile
import models.UserProfile
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification

class SqlHarvestEventServiceSpec extends PlaySpecification {

  private val actorSystem = new GuiceApplicationBuilder().build().injector.instanceOf[ActorSystem]
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  def service(implicit db: Database) = SqlHarvestEventService(db, actorSystem)

  "Harvest event service" should {
    "create items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      val handle = await(service.save("r1", "default", "1234"))
      await(handle.close())

      val seq = await(service.get("r1", Some("default")))
      seq.headOption must beSome.which(_.jobId must_== "1234")
    }
  }
}
