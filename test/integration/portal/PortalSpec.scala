package integration.portal

import scala.concurrent.ExecutionContext.Implicits.global
import helpers.Neo4jRunnerSpec
import models._
import utils.ContributionVisibility
import controllers.portal.ReversePortal
import backend.ApiUser
import com.google.common.net.HttpHeaders
import defines.EntityType
import backend.rest.PermissionDenied
import play.api.test.FakeRequest


class PortalSpec extends Neo4jRunnerSpec(classOf[PortalSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

  override def getConfig = Map("recaptcha.skip" -> true)

  "Portal views" should {
    "show index page" in new FakeApp {
      val doc = route(FakeRequest(GET, portalRoutes.index().url)).get
      status(doc) must equalTo(OK)
    }

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
      contentAsString(doc) must not contain testBody

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
      contentAsString(doc) must not contain testBody
    }

    "disallow creating annotations without permission" in new FakeApp {
      val testBody = "Test Annotation!!!"
      val testData = Map(
        AnnotationF.BODY -> Seq(testBody),
        ContributionVisibility.PARAM -> Seq(ContributionVisibility.Me.toString)
      )

      val post = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        portalRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testData).get
      status(post) must throwA[PermissionDenied]
    }

    "allow updating and deleting annotations created by a given user" in new FakeApp {

      // First we need to grant permission by adding the user to the portal group
      val addGroup = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.core.routes.Groups
            .addMemberPost("portal", EntityType.UserProfile, unprivilegedUser.id).url), "").get
      status(addGroup) must equalTo(SEE_OTHER)

      val testBody = "Test Annotation!!!"
      val testData = Map(
        AnnotationF.BODY -> Seq(testBody),
        ContributionVisibility.PARAM -> Seq(ContributionVisibility.Me.toString)
      )

      val post = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        portalRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testData).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      header(HttpHeaders.LOCATION, post) must beSome.which { loc =>
        val aid = loc.substring(loc.lastIndexOf("/") + 1)
        val updateBody = "UPDATED TEST ANNOTATION!!!"
        val updateData = testData.updated(AnnotationF.BODY, Seq(updateBody))
        val udpost = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
          portalRoutes.editAnnotationPost(aid).url),
          updateData).get
        status(udpost) must equalTo(OK)
        contentAsString(udpost) must contain(updateBody)

        val delpost = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
          portalRoutes.deleteAnnotationPost(aid).url)).get
        status(delpost) must equalTo(OK)
      }
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
      contentAsString(doc) must not contain testBody

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

    "allow annotation promotion to increase visibility" in new FakeApp {
      val testBody = "Test Annotation!!!"
      val testData = Map(
        AnnotationF.BODY -> Seq(testBody),
        AnnotationF.ALLOW_PUBLIC -> Seq("true"),
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
      contentAsString(doc) must not contain testBody

      // Get a id via faff method and promote the item...
      implicit val apiUser = ApiUser(Some(privilegedUser.id))
      val aid = await(testBackend.getAnnotationsForItem("c4")).head.id
      await(testBackend.promote(aid))

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
