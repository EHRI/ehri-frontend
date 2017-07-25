package integration.admin

import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import controllers.admin.{IndexTypes, Indexing}
import defines.EntityType
import helpers._
import models.{Group, GroupF, UserProfile, UserProfileF}
import play.api.http.MimeTypes
import play.api.libs.json.{JsString, Json}
import play.api.test.{FakeRequest, WithServer}
import utils.search.SearchConstants

import scala.concurrent.{Future, Promise}


/**
  * Spec to test various page views operate as expected.
  */
class SearchSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name = "test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name = "Administrators")))
  )


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
    "perform indexing correctly via Websocket endpoint" in new WithServer(app = appBuilder.build(), port = port) {
      val cmd = List(EntityType.DocumentaryUnit)
      val data = IndexTypes(cmd)
      val wsUrl = s"ws://127.0.0.1:$port${controllers.admin.routes.Indexing.indexer().url}"

      val ws = WebSocketClientWrapper(wsUrl,
        headers = Map(AUTH_TEST_HEADER_NAME -> testAuthToken(privilegedUser.id)))
      try {
        ws.client.connectBlocking()
        ws.client.send(Json.stringify(Json.toJson(data)).getBytes("UTF-8"))

        eventually {
          ws.messages.contains(JsString(Indexing.DONE_MESSAGE).toString)
          indexEventBuffer.lastOption must beSome.which { bufcmd =>
            bufcmd must equalTo(cmd.toString())
          }
        }
      } finally {
        ws.client.closeBlocking()
      }
    }

    "perform indexing correctly via Websocket endpoint 2" in new WithServer(app = appBuilder.build(), port = port) {
      implicit val as = app.actorSystem
      implicit val mat = app.materializer
      import as.dispatcher

      val cmd = List(EntityType.DocumentaryUnit)
      val data = IndexTypes(cmd)
      val wsUrl = s"ws://127.0.0.1:$port${controllers.admin.routes.Indexing.indexer().url}"
      val headers = collection.immutable.Seq(RawHeader(AUTH_TEST_HEADER_NAME, testAuthToken(privilegedUser.id)))

      val source: Source[Message, NotUsed] = Source(List(TextMessage(Json.stringify(Json.toJson(data)))))

      val flow: Flow[Message, Message, (Promise[Option[Message]])] =
        Flow.fromSinkAndSourceMat(Sink.foreach[Message](println), source
          .concatMat(Source.maybe[Message])(Keep.right))(Keep.right)

      val (resp, promise) =
        Http().singleWebSocketRequest(WebSocketRequest(wsUrl, extraHeaders = headers), flow)

      val connected = resp.map { upgrade =>
        if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
          upgrade.response.status
        } else {
          throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
        }
      }

      connected.onComplete(println)
      await(connected) == StatusCodes.SwitchingProtocols

      //promise.success(None)
      //println(await(out))


      //await(out) must_== Seq(TextMessage.Strict(JsString(Indexing.DONE_MESSAGE).toString))
      indexEventBuffer.lastOption must beSome.which { bufcmd =>
        bufcmd must equalTo(cmd.toString())
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
