package integration

import models.{AnnotationF, AccessPointF}
import helpers._
import play.api.libs.json.{JsBoolean, JsValue, Json}
import controllers.generic.{AccessPointLink, Annotate}
import models.LinkF.LinkType

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class APISpec extends Neo4jRunnerSpec(classOf[APISpec]) {

  import mocks.privilegedUser

  "Link JSON endpoints" should {
    "allow creating and reading" in new FakeApp {
      val json = Json.toJson(new AccessPointLink("a1", Some(LinkType.Associative), Some("Test link")))
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.createLink("c1", "ur1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      val cr2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.archdesc.routes.DocumentaryUnits.getLink("c1", "ur1").url)).get
      status(cr2) must equalTo(OK)
      Json.parse(contentAsString(cr2)) mustEqual json
    }

    "allow creating new access points and deleting them" in new FakeApp {
      val ap = new AccessPointF(id = None, accessPointType=AccessPointF.AccessPointType.SubjectAccess, name="Test text")
      val json = Json.toJson(ap)(controllers.generic.AccessPointLink.accessPointFormat)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.createAccessPoint("c1", "cd1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      val jsap: JsValue = contentAsJson(cr)
      val id = (jsap \ "id").as[String]

      val del = route(fakeLoggedInJsonRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.deleteAccessPoint("c1", "cd1", id).url)
          .withHeaders(jsonPostHeaders.toSeq: _*), "").get
      status(del) must equalTo(OK)
    }

    "allow creating new links" in new FakeApp {
      val link = new AccessPointLink("a1", description = Some("Test link"))
      val json = Json.toJson(link)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.createLink("c1", "ur1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      val id = (contentAsJson(cr) \ "id").as[String]
      val del = route(fakeLoggedInJsonRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.deleteLinkAndAccessPoint("c1", "cd1", "ur1", id).url)
          .withHeaders(jsonPostHeaders.toSeq: _*), "").get
      status(del) must equalTo(OK)
      contentAsJson(del) must equalTo(JsBoolean(value = true))
    }
  }

  "Annotation JSON endpoints" should {
    "be able to fetch annotations for an item" in new FakeApp {
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.annotation.routes.Annotations.getAnnotationJson("c1").url)).get
      status(cr) must equalTo(OK)
    }

    "allow creating annotations" in new FakeApp {
      val json = Json.toJson(new AnnotationF(id = None, body = "Hello, world!"))(
        Annotate.clientAnnotationFormat)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.annotation.routes.Annotations.createAnnotationJsonPost("c1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
    }
  }
}
