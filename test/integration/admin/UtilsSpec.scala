package integration.admin

import akka.util.Timeout
import helpers._
import play.api.test.FakeRequest

import java.util.concurrent.TimeUnit
import scala.concurrent.TimeoutException

/**
 * Spec to test various page views operate as expected.
 */
class UtilsSpec extends IntegrationTestRunner with FakeMultipartUpload {

  "Utils" should {
    "return a successful ping of the EHRI REST service and search engine" in new ITestApp {
      val ping = FakeRequest(controllers.admin.routes.Utils.checkServices()).call()
      status(ping) must equalTo(OK)
      contentAsString(ping) must_== "ehri\tok\nsolr\tok"
    }

    "check user sync correctly" in new ITestApp {
      val check = FakeRequest(controllers.admin.routes.Utils.checkUserSync()).call()
      status(check) must equalTo(OK)
      // User joeblogs exists in the account mocks but not the
      // graph DB fixtures, so the sync check should (correcly)
      // highlight this.
      contentAsString(check) must contain("joeblogs")
    }

    "check event stream connects and sends keep-alive events" in new ITestApp(
        specificConfig = Map("ehri.eventStream.keepAlive" -> "100 millis")) {
      val stream = FakeRequest(controllers.admin.routes.Utils.sse()).call()
      val timeout: Timeout = Timeout(150, TimeUnit.MILLISECONDS)
      val mat = app.materializer
      // FIXME: can't currently work out how to test a stream that never finishes.
      // For the moment we're just testing it triggers a timeout exception, but
      // cannot test the sent contents...
      contentAsString(stream)(timeout, mat) must throwA[TimeoutException]
    }
  }
}
