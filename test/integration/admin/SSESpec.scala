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
class SSESpec extends IntegrationTestRunner with FakeMultipartUpload {

  override def getConfig: Map[String, Any] =
    super.getConfig ++ Map("ehri.eventStream.keepAlive" -> "10 millis")

  "Utils" should {

    def client(implicit app: Application) = {
      implicit val mat: Materializer = app.injector.instanceOf[Materializer]
      StandaloneAhcWSClient()
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
