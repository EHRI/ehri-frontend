package integration

import models.{AnnotationF, AccessPointF}
import helpers._
import play.api.libs.json.{JsBoolean, Json}
import controllers.generic.{AccessPointLink, Annotate}
import models.LinkF.LinkType

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class APISpec extends IntegrationTestRunner {

  import mocks.privilegedUser

  "Link JSON endpoints" should {
    "allow creating and reading" in new ITestApp {
      val json = Json.toJson(new AccessPointLink("a1", Some(LinkType.Associative), Some("Test link")))
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.createLink("c1", "ur2").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      val cr2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.archdesc.routes.DocumentaryUnits.getLink("c1", "ur2").url)).get
      status(cr2) must equalTo(OK)
      contentAsJson(cr2) mustEqual json
    }

    "allow creating new access points and deleting them" in new ITestApp {
      val ap = new AccessPointF(id = None, accessPointType=AccessPointF.AccessPointType.SubjectAccess, name="Test text")
      val json = Json.toJson(ap)(controllers.generic.AccessPointLink.accessPointFormat)
      val cr = route(fakeLoggedInJsonRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits
          .createAccessPoint("c1", "cd1").url), json).get
      status(cr) must equalTo(CREATED)
      (contentAsJson(cr) \ "id").asOpt[String] must beSome.which { id =>
        val del = route(fakeLoggedInJsonRequest(privilegedUser, POST,
          controllers.archdesc.routes.DocumentaryUnits
            .deleteAccessPoint("c1", "cd1", id).url), "").get
        status(del) must equalTo(OK)
      }
    }

    "allow creating new links" in new ITestApp {
      val link = new AccessPointLink("a1", description = Some("Test link"))
      val json = Json.toJson(link)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.createLink("c1", "ur1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      (contentAsJson(cr) \ "id").asOpt[String] must beSome.which { id =>
        val del = route(fakeLoggedInJsonRequest(privilegedUser, POST,
          controllers.archdesc.routes.DocumentaryUnits
            .deleteLinkAndAccessPoint("c1", "cd1", "ur1", id).url)
          .withHeaders(jsonPostHeaders.toSeq: _*), "").get
        status(del) must equalTo(OK)
        contentAsJson(del) must equalTo(JsBoolean(value = true))
      }
    }
  }

  "Annotation JSON endpoints" should {
    "allow creating annotations" in new ITestApp {
      val json = Json.toJson(new AnnotationF(id = None, body = "Hello, world!"))(
        Annotate.clientAnnotationFormat)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.annotation.routes.Annotations.createAnnotationJsonPost("c1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
    }
  }
}
