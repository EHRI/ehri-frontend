package integration

import helpers._
import backend.ApiUser
import play.api.test.FakeRequest

/**
 * Spec to test various page views operate as expected.
 */
class SystemEventViewsSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  "System Event views" should {

    "get details of deleted items from the last version" in new ITestApp {

      val del = FakeRequest(controllers.authorities.routes.HistoricalAgents.deletePost("a1"))
        .withUser(privilegedUser).withCsrf.call()
      status(del) must equalTo(SEE_OTHER)
      // After deleting an item, the event should be top of our list and
      // include the item title extracted from the last version
      val events = FakeRequest(controllers.events.routes.SystemEvents.list())
        .withUser(privilegedUser).call()
      contentAsString(events) must contain("An Authority 1")
    }
  }
}
