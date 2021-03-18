package services.harvesting

import helpers._
import mockdata.adminUserProfile
import models.UserProfile
import play.api.db.Database
import play.api.test.PlaySpecification

class SqlHarvestEventServiceSpec extends SimpleAppTest with PlaySpecification {

  def service(implicit db: Database) = SqlHarvestEventService(db, implicitApp.actorSystem)
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  "Harvest event service" should {
    "create items" in withDatabaseFixture("oaipmh-config-fixtures.sql") { implicit db =>
      val handle = await(service.save("r1", "default", "1234"))
      await(handle.close())

      val seq = await(service.get("r1", Some("default")))
      seq.headOption must beSome.which(_.jobId must_== "1234")
    }
  }
}
