package test

import helpers._
import models.{GroupF, Group, UserProfileF, UserProfile}
import defines.EntityType
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.Future
import solr.SolrConstants

/**
 * Spec to test various page views operate as expected.
 */
class ProfileSpec extends Neo4jRunnerSpec(classOf[ProfileSpec]) {

  import mocks.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.profile_id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )


  "Profile views" should {

    "show profile at default URL" in new FakeApp {
      val profile = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.admin.routes.Profile.profile.url)).get
      status(profile) must equalTo(OK)
    }

    "show profile form" in new FakeApp {
      val profileForm = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.admin.routes.Profile.updateProfile.url)).get
      status(profileForm) must equalTo(OK)
    }

    "allow updating profile" in new FakeApp {

      val data = Map[String,Seq[String]](
        "identifier" -> Seq("not-allowed"),
        "name" -> Seq("Updated Name")
      )

      val editPost = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.admin.routes.Profile.updateProfilePost.url), data).get
      status(editPost) must equalTo(SEE_OTHER)

      val profile = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.admin.routes.Profile.profile.url)).get
      contentAsString(profile) must contain("Updated Name")
      contentAsString(profile) must not contain("not-allowed")
    }
  }

  step {
    runner.stop()
  }
}
