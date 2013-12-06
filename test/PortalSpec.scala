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
      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.Portal.profile.url)).get
      status(prof) must equalTo(OK)
    }

    "allow editing profile" in new FakeApp {
      val testName = "Inigo Montoya"
      val data = Map(
        "identifier" -> Seq("???"), // Overridden...
        UserProfileF.NAME -> Seq(testName)
      )
      val update = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.portal.routes.Portal.updateProfilePost.url), data).get
      status(update) must equalTo(SEE_OTHER)

      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.Portal.profile.url)).get
      status(prof) must equalTo(OK)
      contentAsString(prof) must contain(testName)
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

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.Portal.browseDocument("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain(testBody)

    }

    "allow annotating fields with correct visibility" in new FakeApp {
      val testBody = "Test Annotation!!!"
      val testData = Map(
        AnnotationF.BODY -> Seq(testBody),
        ContributionVisibility.PARAM -> Seq(ContributionVisibility.Me.toString)
      )

      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.portal.routes.Portal.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testData).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.Portal.browseDocument("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain(testBody)
    }

    "allow updating annotations" in new FakeApp {

    }

    "allow changing annotation visibility" in new FakeApp {
      val testBody = "Test Annotation!!!"
      val testData = Map(
        AnnotationF.BODY -> Seq(testBody),
        ContributionVisibility.PARAM -> Seq(ContributionVisibility.Me.toString)
      )

      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.portal.routes.Portal.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testData).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.Portal.browseDocument("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain(testBody)

      // Mmmn, need to get the id - this is faffy... assume there is
      // only one annotation on the item and fetch it via the api...
      implicit val apiUser = ApiUser(Some(privilegedUser.id))
      val aid = await(testBackend.getAnnotationsForItem("c4")).head.id

      // The privilegedUser and unprivilegedUser belong to the same group (kcl)
      // so if we set the visibility to groups it should be visible to the other
      // guy...
      val visData = Map(
        ContributionVisibility.PARAM -> Seq(ContributionVisibility.Groups.toString)
      )
      val setVis = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.portal.routes.Portal.setAnnotationVisibilityPost(aid).url), visData).get
      status(setVis) must equalTo(OK)

      // Ensure the unprivileged user CAN now see the annotation...
      val doc2 = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.Portal.browseDocument("c4").url)).get
      status(doc2) must equalTo(OK)
      contentAsString(doc2) must contain(testBody)
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
