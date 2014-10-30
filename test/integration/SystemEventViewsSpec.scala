package integration

import helpers._
import backend.ApiUser

/**
 * Spec to test various page views operate as expected.
 */
class SystemEventViewsSpec extends IntegrationTestRunner {
  import mocks.privilegedUser

  "System Event views" should {

    "get details of deleted items from the last version" in new ITestApp {

      val del = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.authorities.routes.HistoricalAgents.deletePost("a1").url)).get
      status(del) must equalTo(SEE_OTHER)
      // After deleting an item, the event should be top of our list and
      // include the item title extracted from the last version
      val events = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.admin.routes.SystemEvents.list().url)).get
      contentAsString(events) must contain("An Authority 1")
    }
  }
}
