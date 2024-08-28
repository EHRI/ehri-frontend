package integration.admin

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import controllers.admin.IndexTypes
import helpers._
import models.EntityType
import play.api.http.MimeTypes
import play.api.libs.json.{JsString, Json}
import play.api.test.FakeRequest
import services.search.SearchConstants

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}


/**
  * Spec to test various page views operate as expected.
  */
class SearchSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  "Search views" should {

    "search for hierarchical items with no query should apply a top-level filter" in new ITestApp {
      val search = FakeRequest(controllers.units.routes.DocumentaryUnits.search())
        .withUser(privilegedUser)
        .call()
      status(search) must equalTo(OK)
      searchParamBuffer
        .last.filters.get(SearchConstants.TOP_LEVEL) must beSome(true)
    }

    "search for hierarchical item with a query should not apply a top-level filter" in new ITestApp {
      val search = FakeRequest(GET, controllers.units.routes.DocumentaryUnits.search().url + "?q=foo")
        .withUser(privilegedUser)
        .call()
      status(search) must equalTo(OK)
      searchParamBuffer
        .last.filters.get(SearchConstants.TOP_LEVEL) must beNone
    }
  }

  "Search index mediator" should {
    val port = 9902
    "perform indexing correctly via Websocket endpoint" in new ITestServer(
        app = appBuilder.configure(getConfig).build(), port = port) {

      val cmd = List(EntityType.DocumentaryUnit)
      val data = IndexTypes(cmd)
      val wsUrl = s"ws://127.0.0.1:$port${controllers.admin.routes.Indexing.indexer().url}"
      val headers = collection.immutable.Seq(RawHeader(AUTH_TEST_HEADER_NAME, testAuthToken(privilegedUser.id)))

      // Ensure the buffer of index events is empty.
      indexEventBuffer.clear()

      // Perform the indexing operation.
      val source: Source[Message, NotUsed] = Source(
        List(TextMessage(Json.stringify(Json.toJson(data)))))

      val outFlow: Flow[Message, Message, (Future[Seq[Message]], Promise[Option[Message]])] =
        Flow.fromSinkAndSourceMat(Sink.seq[Message], source
          .concatMat(Source.maybe[Message])(Keep.right))(Keep.both)

      val (resp, (out, promise)) =
        Http().singleWebSocketRequest(WebSocketRequest(wsUrl, extraHeaders = headers), outFlow)

      val connected = resp.map { upgrade =>
        if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
          upgrade.response.status
        } else {
          throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
        }
      }

      await(connected) must_== StatusCodes.SwitchingProtocols

      // Wait for the buffer to contain what we expect
      indexEventBuffer.lastOption must beSome
        .eventually(retries = 50, sleep = 100.millis)
        indexEventBuffer.last must contain(EntityType.DocumentaryUnit.toString)

      // close the connection...
      promise.success(None)

      // Check the last WS message is as expected
      await(out).lastOption must beSome.which { (msg: Message) =>
        msg must_== TextMessage.Strict(JsString(utils.WebsocketConstants.DONE_MESSAGE).toString)
      }
    }
  }

  "Search metrics" should {
    "response to JSON" in new ITestApp {
      val repoMetrics = FakeRequest(controllers.admin.routes.Metrics.repositoryCountries())
        .withUser(privilegedUser)
        .accepting(MimeTypes.JSON)
        .call()
      status(repoMetrics) must equalTo(OK)
      contentType(repoMetrics) must beSome(MimeTypes.JSON)
    }
  }
}
