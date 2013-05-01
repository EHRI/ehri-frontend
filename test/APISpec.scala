package test

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.concurrent.Execution.Implicits._
import models.AccessPointF
import controllers.routes
import helpers._
import play.api.libs.json.Json
import controllers.base.{NewAccessPointLink, AccessPointLink}

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class APISpec extends Neo4jRunnerSpec(classOf[APISpec]) {

  import mocks.UserFixtures.{privilegedUser, unprivilegedUser}

  "Link JSON endpoints should" should {
    "allow creating and reading" in new FakeApp {
      val json = Json.toJson(new AccessPointLink("a1", description = Some("Test link")))
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createLinkJson("c1", "ur1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      val cr2 = route(fakeLoggedInRequest(privilegedUser, GET,
        routes.DocumentaryUnits.getLinkJson("c1", "ur1").url)).get
      status(cr2) must equalTo(OK)
      Json.parse(contentAsString(cr2)) mustEqual json
    }

    "allow creating new access points along with a link" in new FakeApp {
      val link = new AccessPointLink("a1", description = Some("Test link"))
      val apdata = new NewAccessPointLink("Test Access Point", AccessPointF.AccessPointType.SubjectAccess, link)
      val json = Json.toJson(apdata)
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createAccessPointLinkJson("c1", "cd1").url)
        .withHeaders(jsonPostHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      println(contentAsString(cr))
    }
  }

  step {
    runner.stop
  }
}
