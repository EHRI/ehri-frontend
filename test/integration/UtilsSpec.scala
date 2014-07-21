package integration

import helpers._
import play.api.test.FakeRequest

/**
 * Spec to test various page views operate as expected.
 */
class UtilsSpec extends Neo4jRunnerSpec(classOf[UtilsSpec]) {

  "Utils" should {
    "return a successful ping of the EHRI REST service" in new FakeApp {
      val ping = route(FakeRequest(GET,
          controllers.adminutils.routes.Utils.checkDb().url)).get
      status(ping) must equalTo(OK)
    }

    "check user sync correctly" in new FakeApp {
      val check = route(FakeRequest(GET,
        controllers.adminutils.routes.Utils.checkUserSync().url)).get
      status(check) must equalTo(OK)
      // User joeblogs exists in the account mocks but not the
      // graph DB fixtures, so the sync check should (correcly)
      // highlight this.
      contentAsString(check) must contain("joeblogs")
    }
  }
}
