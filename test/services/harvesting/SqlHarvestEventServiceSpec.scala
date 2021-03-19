package services.harvesting

import helpers._
import mockdata.adminUserProfile
import models.UserProfile
import play.api.Application

class SqlHarvestEventServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlHarvestEventService]
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  "Harvest event service" should {
    "create items" in new DBTestApp("oaipmh-config-fixtures.sql") {
      val handle = await(service.save("r1", "default", "1234"))
      await(handle.close())

      val seq = await(service.get("r1", Some("default")))
      seq.headOption must beSome.which(_.jobId must_== "1234")
    }
  }
}
