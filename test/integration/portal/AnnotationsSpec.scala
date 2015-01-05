package integration.portal

import helpers.IntegrationTestRunner
import models._
import utils.ContributionVisibility
import controllers.portal.ReversePortal
import controllers.portal.annotate.ReverseAnnotations
import backend.ApiUser
import com.google.common.net.HttpHeaders
import defines.EntityType
import backend.rest.PermissionDenied
import play.api.test.FakeRequest


class AnnotationsSpec extends IntegrationTestRunner {
  import mocks.{privilegedUser, unprivilegedUser, moderator}

  private val annotationRoutes: ReverseAnnotations = controllers.portal.annotate.routes.Annotations
  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

  override def getConfig = Map("recaptcha.skip" -> true)

  private val testAnnotationBody = "Test Annotation!!!"
  private val testAnnotation: Map[String,Seq[String]] = Map(
    AnnotationF.BODY -> Seq(testAnnotationBody),
    ContributionVisibility.PARAM -> Seq(ContributionVisibility.Me.toString)
  )
  private def testPromotableAnnotation: Map[String,Seq[String]] =
    testAnnotation.updated(AnnotationF.IS_PRIVATE, Seq(false.toString))

  "Portal annotation views" should {
    "allow annotating items with correct visibility" in new ITestApp {
      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        annotationRoutes.annotatePost("c4", "cd4").url), testAnnotation).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testAnnotationBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain testAnnotationBody

    }

    "allow annotating fields with correct visibility" in new ITestApp {
      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        annotationRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testAnnotation).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testAnnotationBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain testAnnotationBody
    }

    "disallow creating annotations without permission" in new ITestApp {
      val post = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        annotationRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testAnnotation).get
      status(post) must throwA[PermissionDenied]
    }

    "allow updating and deleting annotations created by a given user" in new ITestApp {

      // First we need to grant permission by adding the user to the portal group
      val addGroup = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.users.routes.UserProfiles
            .addToGroup(unprivilegedUser.id, "portal").url), "").get
      status(addGroup) must equalTo(SEE_OTHER)

      val post = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        annotationRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testAnnotation).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testAnnotationBody)

      header(HttpHeaders.LOCATION, post) must beSome.which { loc =>
        val aid = loc.substring(loc.lastIndexOf("/") + 1)
        val updateBody = "UPDATED TEST ANNOTATION!!!"
        val updateData = testAnnotation.updated(AnnotationF.BODY, Seq(updateBody))
        val udpost = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
          annotationRoutes.editAnnotationPost(aid).url),
          updateData).get
        status(udpost) must equalTo(OK)
        contentAsString(udpost) must contain(updateBody)

        val delpost = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
          annotationRoutes.deleteAnnotationPost(aid).url)).get
        status(delpost) must equalTo(OK)
      }
    }

    "allow changing annotation visibility" in new ITestApp {
      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        annotationRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testAnnotation).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testAnnotationBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain testAnnotationBody

      // Mmmn, need to get the id - this is faffy... assume there is
      // only one annotation on the item and fetch it via the api...
      implicit val apiUser = ApiUser(Some(privilegedUser.id))
      await(testBackend.getAnnotationsForItem[Annotation]("c4")).headOption must beSome.which { aid =>
      // The privilegedUser and unprivilegedUser belong to the same group (kcl)
      // so if we set the visibility to groups it should be visible to the other
      // guy...
        val visData = Map(
          ContributionVisibility.PARAM -> Seq(ContributionVisibility.Groups.toString)
        )
        val setVis = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          annotationRoutes.setAnnotationVisibilityPost(aid.id).url), visData).get
        status(setVis) must equalTo(OK)

        // Ensure the unprivileged user CAN now see the annotation...
        val doc2 = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
          controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
        status(doc2) must equalTo(OK)
        contentAsString(doc2) must contain(testAnnotationBody)

      }

    }

    "allow annotation promotion to increase visibility" in new ITestApp {
      val testBody = "Test Annotation!!!"
      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        annotationRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testPromotableAnnotation).get
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
        controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain testBody

      // Get a id via faff method and promote the item...
      implicit val apiUser = ApiUser(Some(privilegedUser.id))
      val aid = await(testBackend.getAnnotationsForItem[Annotation]("c4")).headOption must beSome.which { aid =>
        await(testBackend.promote[Annotation](aid.id))

        // Ensure the unprivileged user CAN now see the annotation...
        val doc2 = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
          controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
        status(doc2) must equalTo(OK)
        contentAsString(doc2) must contain(testBody)
      }
    }

    "give moderator groups visibility to promotable items" in new ITestApp {
      val post = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        annotationRoutes.annotateFieldPost(
          "c4", "cd4", IsadG.SCOPE_CONTENT).url), testPromotableAnnotation).get
      status(post) must equalTo(CREATED)
      header(HttpHeaders.LOCATION, post) must beSome.which { url =>

        val id = url.substring(url.lastIndexOf("/") + 1)
        contentAsString(post) must contain(testAnnotationBody)

        // Ensure the unprivileged user can't see the annotation...
        val check1 = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET,
          controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
        status(check1) must equalTo(OK)
        contentAsString(check1) must not contain testAnnotationBody

        // Moderators can see the annotation
        val check2 = route(fakeLoggedInHtmlRequest(moderator, GET,
          controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
        status(check2) must equalTo(OK)
        contentAsString(check2) must contain(testAnnotationBody)

        val edit = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          annotationRoutes.editAnnotationPost(id).url),
          testAnnotation.updated(AnnotationF.IS_PRIVATE, Seq(true.toString))).get
        status(edit) must equalTo(OK)

        // Now the moderator cannot see the item...
        val check3 = route(fakeLoggedInHtmlRequest(moderator, GET,
          controllers.portal.routes.DocumentaryUnits.browse("c4").url)).get
        status(check3) must equalTo(OK)
        contentAsString(check3) must not contain testAnnotationBody
      }
    }

    "allow deleting annotations" in new ITestApp {
      val del = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        annotationRoutes.deleteAnnotation("ann5").url)).get
      status(del) must equalTo(OK)
    }
  }
}
