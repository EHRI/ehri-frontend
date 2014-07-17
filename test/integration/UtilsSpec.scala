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
  }
}
