package test

import helpers.{formPostHeaders,Neo4jRunnerSpec}
import models._
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers.await
import play.api.http.{MimeTypes, HeaderNames}
import backend.rest.PermissionDenied
import utils.ContributionVisibility
import backend.ApiUser
import scala.concurrent.Future


class PortalSpec extends Neo4jRunnerSpec(classOf[PortalSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )

  "Portal views" should {
    "view docs" in new FakeApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.Portal.browseDocument("c1").url)).get
      status(doc) must equalTo(OK)
    }

    "view repositories" in new FakeApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.Portal.browseRepository("r1").url)).get
      status(doc) must equalTo(OK)
    }

    "view historical agents" in new FakeApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.Portal.browseHistoricalAgent("a1").url)).get
      status(doc) must equalTo(OK)
    }

    "allow viewing profile" in new FakeApp {

    }

    "allow editing profile" in new FakeApp {

    }

    "allow following and unfollowing users" in new FakeApp {

    }

    "allow watching and unwatching items" in new FakeApp {

    }

    "show correct activity for watched items and followed users" in new FakeApp {

    }

    "allow annotating items with correct visibility" in new FakeApp {
      val testBody = "Test Annotation!!!"
      val testData = Map(
        AnnotationF.BODY -> Seq(testBody),
        ContributionVisibility.PARAM -> Seq(ContributionVisibility.Me.toString)
      )

      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.portal.routes.Portal.annotatePost("c4", "cd4").url), testData).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      implicit val apiUser = ApiUser(Some(unprivilegedUser.id))
      private val items = await(testBackend.getAnnotationsForItem("c4"))
      println("Annotations for " + unprivilegedUser.id + " -> " + items.map(_.model.body))

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.Portal.browseDocument("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain(testBody)

    }

    "allow updating annotations" in new FakeApp {

    }

    "allow changing annotation visibility" in new FakeApp {

    }

    "allow deleting annotations" in new FakeApp {

    }

    "allow linking items" in new FakeApp {

    }

    "allow deleting links" in new FakeApp {

    }

    "allow changing link visibility" in new FakeApp {

    }
  }
}
