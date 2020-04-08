package integration.admin

import helpers._
import models._


class RepositoryDataSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  private val repoDataRoutes = controllers.institutions.routes.RepositoryData

  // Mock user who belongs to admin
  val userProfile = UserProfile(
    data = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )

  "Repository Data API" should {

    "provide PUT urls" in new ITestApp {
    }
  }
}
