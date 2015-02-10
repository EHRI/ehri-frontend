package integration

import controllers.generic.AccessPointLink
import helpers._
import models.{AnnotationF, AccessPointF}
import models.LinkF.LinkType
import play.api.libs.json.{JsBoolean, Json}

import scala.util.{Success, Failure, Try}

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class APISpec extends IntegrationTestRunner {

  import mocks.privilegedUser

  "Link JSON endpoints" should {
    "allow creating and reading" in new ITestApp {
      val json = Json.toJson(new AccessPointLink("a1", Some(LinkType.Associative), Some("Test link")))
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.units.routes.DocumentaryUnits.createLink("c1", "ur2").url), json).get
      status(cr) must equalTo(CREATED)
      val cr2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.units.routes.DocumentaryUnits.getLink("c1", "ur2").url)).get
      status(cr2) must equalTo(OK)
      contentAsJson(cr2) mustEqual json
    }

    "allow creating new access points and deleting them" in new ITestApp {
      val ap = new AccessPointF(id = None, accessPointType=AccessPointF.AccessPointType.SubjectAccess, name="Test text")
      val json = Json.toJson(ap)(controllers.generic.AccessPointLink.accessPointFormat)
      val cr = route(fakeLoggedInJsonRequest(privilegedUser, POST,
        controllers.units.routes.DocumentaryUnits
          .createAccessPoint("c1", "cd1").url), json).get
      status(cr) must equalTo(CREATED)
      (contentAsJson(cr) \ "id").asOpt[String] must beSome.which { id =>
        val del = route(fakeLoggedInJsonRequest(privilegedUser, POST,
          controllers.units.routes.DocumentaryUnits
            .deleteAccessPoint("c1", "cd1", id).url), "").get
        status(del) must equalTo(OK)
      }
    }

    "allow creating new links" in new ITestApp {
      val link = new AccessPointLink("a1", description = Some("Test link"))
      val json = Json.toJson(link)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.units.routes.DocumentaryUnits.createLink("c1", "ur1").url), json).get
      status(cr) must equalTo(CREATED)
      (contentAsJson(cr) \ "id").asOpt[String] must beSome.which { id =>
        val del = route(fakeLoggedInJsonRequest(privilegedUser, POST,
          controllers.units.routes.DocumentaryUnits
            .deleteLinkAndAccessPoint("c1", "cd1", "ur1", id).url), "").get
        status(del) must equalTo(OK)
        contentAsJson(del) must equalTo(JsBoolean(value = true))
      }
    }

    "allow creating lots of links in many concurrent requests" in new ITestApp {
      import play.api.mvc.Result

      import scala.concurrent.Future
      val ops: Future[List[Try[Result]]] = Future.sequence {
        1.to(100).toList.map { i =>
          val link = new AccessPointLink("a1", description = Some(s"Test link $i"))
          val json = Json.toJson(link)
          route(fakeLoggedInHtmlRequest(privilegedUser, POST,
            controllers.units.routes.DocumentaryUnits.createLink("c1", "ur1").url), json).get.map { r =>
              Success(r)
          } recover {
            case e => Failure(e)
          }
        }
      }

      val results: List[Try[Result]] = await(ops)
      forall(results)(_ must beSuccessfulTry.which { r =>
        r.header.status must equalTo(CREATED)
      })
    }
  }

  "Annotation JSON endpoints" should {
    "allow creating annotations" in new ITestApp {
      val json = Json.toJson(new AnnotationF(id = None, body = "Hello, world!"))(
        AnnotationF.Converter.clientFormat)
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.annotation.routes.Annotations.createAnnotationJsonPost("c1").url), json).get
      status(cr) must equalTo(CREATED)
    }
  }
}
