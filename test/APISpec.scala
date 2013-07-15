package test

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.concurrent.Execution.Implicits._
import models.{AnnotationF, AccessPointF}
import controllers.routes
import helpers._
import play.api.libs.json.Json
import controllers.base.{EntityAnnotate, AccessPointLink}

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class APISpec extends Neo4jRunnerSpec(classOf[APISpec]) {

  import mocks.UserFixtures.{privilegedUser, unprivilegedUser}

  "Link JSON endpoints" should {
    "allow creating and reading" in new FakeApp {
      val json = Json.toJson(new AccessPointLink("a1", description = Some("Test link")))
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createLink("c1", "ur1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      val cr2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        routes.DocumentaryUnits.getLink("c1", "ur1").url)).get
      status(cr2) must equalTo(OK)
      Json.parse(contentAsString(cr2)) mustEqual json
    }

    "allow creating new access points" in new FakeApp {
      val ap = new AccessPointF(id = None, accessPointType=AccessPointF.AccessPointType.SubjectAccess, name="Test text")
      val json = Json.toJson(ap)(controllers.base.AccessPointLink.accessPointFormat)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createAccessPoint("c1", "cd1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      println(contentAsString(cr))
    }

    "allow creating new links" in new FakeApp {
      val link = new AccessPointLink("a1", description = Some("Test link"))
      val json = Json.toJson(link)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createLink("c1", "ur1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      println(contentAsString(cr))
    }
  }

  "Annotation JSON endpoints" should {
    "allow creating annotations" in new FakeApp {
      val json = Json.toJson(new AnnotationF(id = None, body = "Hello, world!"))(
        EntityAnnotate.clientAnnotationFormat)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.core.routes.Annotations.createAnnotationJsonPost("c1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
    }

    "be able to fetch annotations for an item" in new FakeApp {
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.core.routes.Annotations.getAnnotationJson("c1").url)).get
      status(cr) must equalTo(OK)
      println(contentAsString(cr))
    }
  }

  step {
    runner.stop
  }
}
