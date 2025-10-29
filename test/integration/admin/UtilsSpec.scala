package integration.admin

import helpers._
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import play.api.Application
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.test.FakeRequest

/**
 * Spec to test various page views operate as expected.
 */
class UtilsSpec extends IntegrationTestRunner with FakeMultipartUpload {

  override def getConfig: Map[String, Any] =
    super.getConfig ++ Map("ehri.eventStream.keepAlive" -> "10 millis")

  "Utils" should {

    def client(implicit app: Application) = {
      implicit val mat: Materializer = app.injector.instanceOf[Materializer]
      StandaloneAhcWSClient()
    }

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


    "check event stream connects and sends keep-alive events" in new ITestServer() {
      val req = client.url(s"http://localhost:${this.port}${controllers.admin.routes.Utils.sse()}")
        .withHttpHeaders("Accept" -> "text/event-stream")
        .stream()

      val resp = await(req)
      resp.status must_== OK
      resp.contentType must_== "text/event-stream"

      val events: Seq[String] = await(resp.bodyAsSource.take(3).map(_.utf8String).runWith(Sink.seq))
      events.head must contain("data:")
    }
  }
}
