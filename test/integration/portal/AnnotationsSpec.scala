package integration.portal

import helpers.IntegrationTestRunner
import models._
import controllers.portal.annotate.ReverseAnnotations
import com.google.common.net.HttpHeaders
import play.api.test.FakeRequest
import services.data.{DataUser, PermissionDenied}


class AnnotationsSpec extends IntegrationTestRunner {
  import mockdata.{privilegedUser, unprivilegedUser, moderator}

  private val annotationRoutes: ReverseAnnotations = controllers.portal.annotate.routes.Annotations

  override def getConfig = Map("recaptcha.skip" -> true)

  private val testAnnotationBody = "Test Annotation!!!"
  private val testAnnotation: Map[String,Seq[String]] = Map(
    AnnotationF.BODY -> Seq(testAnnotationBody)
  )
  private def testPromotableAnnotation: Map[String,Seq[String]] =
    testAnnotation.updated(AnnotationF.IS_PUBLIC, Seq(true.toString))

  "Portal annotation views" should {
    "allow annotating items with correct visibility" in new ITestApp {
      val post = FakeRequest(annotationRoutes.annotatePost("c4", "cd4"))
        .withUser(privilegedUser).withCsrf.callWith(testAnnotation)
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testAnnotationBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4"))
        .withUser(unprivilegedUser).withCsrf.call()
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain testAnnotationBody

    }

    "allow annotating fields with correct visibility" in new ITestApp {
      val post = FakeRequest(annotationRoutes.annotateFieldPost("c4", "cd4", IsadG.SCOPE_CONTENT))
        .withUser(privilegedUser).withCsrf.callWith(testAnnotation)
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testAnnotationBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4"))
        .withUser(unprivilegedUser).withCsrf.call()
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain testAnnotationBody
    }

    "disallow script injection" in new ITestApp {
      val innocuousText = "This is <b>okay</b>"
      val evilText = "This is bad"
      val evilScript = s"<script>alert('$evilText');</script>"
      val badAnnotation = Map(
        AnnotationF.BODY -> Seq(innocuousText + evilScript)
      )
      val post = FakeRequest(annotationRoutes.annotateFieldPost("c4", "cd4", IsadG.SCOPE_CONTENT))
        .withUser(privilegedUser).withCsrf.callWith(badAnnotation)
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(innocuousText)
      contentAsString(post) must not contain evilText

      // Also text the global and personal list views
      val list = FakeRequest(annotationRoutes.searchAll()).withUser(privilegedUser).withCsrf.call()
      contentAsString(list) must contain(innocuousText)
      contentAsString(list) must not contain evilText

      val userList = FakeRequest(controllers.portal.users.routes.UserProfiles.annotations())
        .withUser(privilegedUser).withCsrf.call()
      contentAsString(userList) must contain(innocuousText)
      contentAsString(userList) must not contain evilText
    }

    "disallow creating annotations without permission" in new ITestApp {
      val post = FakeRequest(annotationRoutes.annotateFieldPost("c4", "cd4", IsadG.SCOPE_CONTENT))
        .withUser(unprivilegedUser).withCsrf.callWith(testAnnotation)
      status(post) must throwA[PermissionDenied]
    }

    "allow updating and deleting annotations created by a given user" in new ITestApp {

      // First we need to grant permission by adding the user to the portal group
      val addGroup = FakeRequest(controllers.users.routes.UserProfiles
            .addToGroup(unprivilegedUser.id, "portal"))
        .withUser(privilegedUser).withCsrf.call()
      status(addGroup) must equalTo(SEE_OTHER)

      val post = FakeRequest(annotationRoutes.annotateFieldPost("c4", "cd4", IsadG.SCOPE_CONTENT))
        .withUser(unprivilegedUser).withCsrf.callWith(testAnnotation)
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testAnnotationBody)

      header(HttpHeaders.LOCATION, post) must beSome.which { loc =>
        val aid = loc.substring(loc.lastIndexOf("/") + 1)
        val updateBody = "UPDATED TEST ANNOTATION!!!"
        val updateData = testAnnotation.updated(AnnotationF.BODY, Seq(updateBody))
        val udpost = FakeRequest(annotationRoutes.editAnnotationPost(aid))
          .withUser(unprivilegedUser).withCsrf.callWith(updateData)
        status(udpost) must equalTo(OK)
        contentAsString(udpost) must contain(updateBody)

        val delpost = FakeRequest(annotationRoutes.deleteAnnotationPost(aid))
          .withUser(unprivilegedUser).withCsrf.call()
        status(delpost) must equalTo(OK)
      }
    }

    "allow annotation promotion to increase visibility" in new ITestApp {
      val testBody = "Test Annotation!!!"
      val post = FakeRequest(annotationRoutes.annotateFieldPost("c4", "cd4", IsadG.SCOPE_CONTENT))
        .withUser(privilegedUser).withCsrf.callWith(testPromotableAnnotation)
      status(post) must equalTo(CREATED)
      contentAsString(post) must contain(testBody)

      // Ensure the unprivileged user can't see the annotation...
      val doc = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4"))
        .withUser(unprivilegedUser).call()
      status(doc) must equalTo(OK)
      contentAsString(doc) must not contain testBody

      // Get a id via faff method and promote the item...
      implicit val apiUser = DataUser(Some(privilegedUser.id))
      val aid = await(dataApi.annotations[Annotation]("c4")).headOption must beSome.which { aid =>
        await(dataApi.promote[Annotation](aid.id))

        // Ensure the unprivileged user CAN now see the annotation...
        val doc2 = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4"))
          .withUser(unprivilegedUser).call()
        status(doc2) must equalTo(OK)
        contentAsString(doc2) must contain(testBody)
      }
    }

    "give moderator groups visibility to promotable items" in new ITestApp {
      val post = FakeRequest(annotationRoutes.annotateFieldPost("c4", "cd4", IsadG.SCOPE_CONTENT))
        .withUser(privilegedUser).withCsrf.callWith(testPromotableAnnotation)
      status(post) must equalTo(CREATED)
      header(HttpHeaders.LOCATION, post) must beSome.which { url =>

        val id = url.substring(url.lastIndexOf("/") + 1)
        contentAsString(post) must contain(testAnnotationBody)

        // Ensure the unprivileged user can't see the annotation...
        val check1 = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4"))
          .withUser(unprivilegedUser).call()
        status(check1) must equalTo(OK)
        contentAsString(check1) must not contain testAnnotationBody

        // Moderators can see the annotation
        val check2 = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4"))
          .withUser(moderator).call()
        status(check2) must equalTo(OK)
        contentAsString(check2) must contain(testAnnotationBody)

        val edit = FakeRequest(annotationRoutes.editAnnotationPost(id))
          .withUser(privilegedUser).withCsrf.callWith(
          testAnnotation.updated(AnnotationF.IS_PUBLIC, Seq(false.toString)))
        status(edit) must equalTo(OK)

        // Now the moderator cannot see the item...
        val check3 = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4"))
          .withUser(moderator).call()
        status(check3) must equalTo(OK)
        contentAsString(check3) must not contain testAnnotationBody
      }
    }

    "allow deleting annotations" in new ITestApp {
      val del = FakeRequest(annotationRoutes.deleteAnnotationPost("ann5"))
        .withUser(privilegedUser).withCsrf.call()
      status(del) must equalTo(OK)
    }
  }
}
