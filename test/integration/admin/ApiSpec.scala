package integration.admin

import controllers.generic.AccessPointLink
import helpers._
import models.LinkF.LinkType
import models.{AccessPointF, AnnotationF, EntityType}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.data.Constants

import scala.util.{Failure, Success, Try}

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class ApiSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  private val docRoutes = controllers.units.routes.DocumentaryUnits

  "Link JSON endpoints" should {
    "allow creating and reading" in new ITestApp {
      val json = Json.toJson(new AccessPointLink("a1", Some(LinkType.Associative), Some("Test link")))
      val cr = FakeRequest(docRoutes.createLink("c1", "ur2"))
        .withUser(privilegedUser).withCsrf.callWith(json)
      status(cr) must equalTo(CREATED)
      val cr2 = FakeRequest(docRoutes.getLink("c1", "ur2"))
        .withUser(privilegedUser).call()
      status(cr2) must equalTo(OK)
      contentAsJson(cr2) mustEqual json
    }

    "allow creating new access points and deleting them" in new ITestApp {
      val ap = new AccessPointF(id = None, accessPointType=AccessPointF.AccessPointType.SubjectAccess, name="Test text")
      val json = Json.toJson(ap)(controllers.generic.AccessPointLink.accessPointFormat)
      val cr = FakeRequest(docRoutes.createAccessPoint("c1", "cd1"))
        .withUser(privilegedUser).withCsrf.callWith(json)
      status(cr) must equalTo(CREATED)
      (contentAsJson(cr) \ "id").asOpt[String] must beSome.which { id =>
        val del = FakeRequest(docRoutes.deleteAccessPoint("c1", "cd1", id))
          .withUser(privilegedUser).withCsrf.call()
        status(del) must equalTo(OK)
      }
    }

    "allow creating new links" in new ITestApp {
      val link = new AccessPointLink("a1", description = Some("Test link"))
      val json = Json.toJson(link)
      val cr = FakeRequest(docRoutes.createLink("c1", "ur1"))
        .withUser(privilegedUser).withCsrf.callWith(json)
      status(cr) must equalTo(CREATED)
      (contentAsJson(cr) \ "id").asOpt[String] must beSome.which { id =>
        val del = FakeRequest(docRoutes.deleteLinkAndAccessPoint("c1", "cd1", "ur1", id))
          .withUser(privilegedUser).withCsrf.call()
        status(del) must equalTo(OK)
        contentAsJson(del) must equalTo(Json.obj("ok" -> true))
      }
    }

    "allow creating lots of links in many concurrent requests" in new ITestApp {
      import play.api.mvc.Result

      import scala.concurrent.Future
      val ops: Future[List[Try[Result]]] = Future.sequence {
        1.to(50).toList.map { i =>
          val link = new AccessPointLink("a1", description = Some(s"Test link $i"))
          val json = Json.toJson(link)
          FakeRequest(docRoutes.createLink("c1", "ur1")).withUser(privilegedUser).withCsrf.callWith(json).map { r =>
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
      val cr = FakeRequest(controllers.annotation.routes.Annotations.createAnnotationJsonPost("c1"))
        .withUser(privilegedUser).withCsrf.callWith(json)
      status(cr) must equalTo(CREATED)
    }
  }

  "Direct API access for GET requests" should {
    "stream with chunked encoding" in new ITestApp {
      val c = FakeRequest(controllers.admin.routes.Data
        .forward(s"classes/${EntityType.DocumentaryUnit.toString}"))
        .withHeaders(Constants.STREAM_HEADER_NAME -> "true")
        .withUser(privilegedUser).call()
      status(c) must equalTo(OK)
      headers(c).get(CONTENT_LENGTH) must beNone
      contentType(c) must beSome("application/json")
      headers(c).get(TRANSFER_ENCODING) must beSome.which { (e: String) =>
        e must_== "chunked"
      }
    }

    "stream with length" in new ITestApp {
      val c = FakeRequest(controllers.admin.routes.Data
        .forward(s"classes/${EntityType.DocumentaryUnit}/c1"))
        .withUser(privilegedUser).call()
      status(c) must equalTo(OK)
      contentType(c) must beSome("application/json")
      headers(c).get(CONTENT_LENGTH) must beSome.which { (l: String) =>
        l.toInt must beGreaterThan(0)
      }
      headers(c).get(TRANSFER_ENCODING) must beNone
    }

    "return the right content type" in new ITestApp {
      val c = FakeRequest(controllers.admin.routes.Data
        .forward("tools/schema"))
        .withUser(privilegedUser).call()
      contentAsString(c) must contain(EntityType.DocumentaryUnit.toString)
      status(c) must equalTo(OK)
      contentType(c) must beSome("text/turtle")
    }
  }
}
