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
import controllers.portal.ReversePortal


class PortalSpec extends Neo4jRunnerSpec(classOf[PortalSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

  "Portal views" should {
    "view docs" in new FakeApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.browseDocument("c1").url)).get
      status(doc) must equalTo(OK)
    }

    "view repositories" in new FakeApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.browseRepository("r1").url)).get
      status(doc) must equalTo(OK)
    }

    "view historical agents" in new FakeApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.browseHistoricalAgent("a1").url)).get
      status(doc) must equalTo(OK)
    }

    "allow viewing profile" in new FakeApp {
      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.profile.url)).get
      status(prof) must equalTo(OK)
    }

    "allow editing profile" in new FakeApp {
      val testName = "Inigo Montoya"
      val data = Map(
        "identifier" -> Seq("???"), // Overridden...
        UserProfileF.NAME -> Seq(testName)
      )
      val update = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        portalRoutes.updateProfilePost.url), data).get
      status(update) must equalTo(SEE_OTHER)

      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.profile.url)).get
      status(prof) must equalTo(OK)
      contentAsString(prof) must contain(testName)
    }

    "allow following and unfollowing users" in new FakeApp {
      val follow = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        portalRoutes.followUser(unprivilegedUser.id).url), "").get
      status(follow) must equalTo(SEE_OTHER)

      val following = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.followingForUser(privilegedUser.id).url)).get
      // Check the following page contains a link to the user we just followed
      contentAsString(following) must contain(
        portalRoutes.browseUser(unprivilegedUser.id).url)

      // Unfollow the sucker - he's boring...
      val unfollow = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        portalRoutes.unfollowUser(unprivilegedUser.id).url), "").get
      status(unfollow) must equalTo(SEE_OTHER)

      val following2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.followingForUser(privilegedUser.id).url)).get
      // Check the following page contains no links to the user we just unfollowed
      contentAsString(following2) must not contain(
        portalRoutes.browseUser(unprivilegedUser.id).url)
    }

    "allow watching and unwatching items" in new FakeApp {
      val watch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        portalRoutes.watchItemPost("c1").url), "").get
      status(watch) must equalTo(SEE_OTHER)

      // Watched items show up on the profile - maybe change this?
      val watching = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.watching.url)).get
      // Check the following page contains a link to the user we just followed
      contentAsString(watching) must contain(
        portalRoutes.browseDocument("c1").url)

      // Unwatch
      val unwatch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        portalRoutes.unwatchItemPost("c1").url), "").get
      status(unwatch) must equalTo(SEE_OTHER)

      val watching2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.watching.url)).get
      // Check the profile contains no links to the item we just unwatched
      contentAsString(watching2) must not contain(
        portalRoutes.browseDocument("c1").url)

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
        portalRoutes.annotatePost("c4", "cd4").url), testData).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        portalRoutes.browseDocument("c4").url)).get
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
        portalRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testData).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        portalRoutes.browseDocument("c4").url)).get
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
        portalRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testData).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        portalRoutes.browseDocument("c4").url)).get
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
        portalRoutes.setAnnotationVisibilityPost(aid).url), visData).get
      status(setVis) must equalTo(OK)

      // Ensure the unprivileged user CAN now see the annotation...
      val doc2 = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        portalRoutes.browseDocument("c4").url)).get
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
