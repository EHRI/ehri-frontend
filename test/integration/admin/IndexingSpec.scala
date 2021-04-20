package integration.admin

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import controllers.admin.IndexTypes
import helpers.SearchTestRunner
import play.api.libs.json.{Json, Writes}
import services.search._
import utils.{Page, PageParams, WebsocketConstants}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}


/**
  * Spec to test the ingest UI and websocket monitoring.
  */
class IndexingSpec extends SearchTestRunner {

  import mockdata.privilegedUser

  def jsonMessage[T](obj: T)(implicit writes: Writes[T]): Message =
    TextMessage.Strict(Json.prettyPrint(Json.toJson(obj)))

  "Indexing views" should {
    val port = 9902
    "index items correctly" in new ITestServer(app = appBuilder.build(), port = port) {

      val mediator = app.injector.instanceOf[SearchIndexMediator]
      val engine = app.injector.instanceOf[SearchEngine]

      // Clear the index so we know we're testing against a clean start
      val query = SearchQuery(paging = PageParams.empty.withoutLimit, mode = SearchMode.DefaultAll)
      await(mediator.handle.clearAll())
      val check = await(engine.search(query))
      check.page must_== Page.empty[SearchHit]

      // Now initiate a full re-index request...
      val headers = collection.immutable.Seq(RawHeader(AUTH_TEST_HEADER_NAME, testAuthToken(privilegedUser.id)))
      val wsUrl = s"ws://127.0.0.1:$port${controllers.admin.routes.Indexing.indexer()}"
      val src = Source.single(jsonMessage(IndexTypes(IndexTypes.all)))

      // NB: using the technique mentioned for "half-closed" websockets here to get output
      // even when we are not putting any items in. We tell the system we want to get the output
      // by closing the server side connection by sending a None message:
      // https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#half-closed-websockets
      val outFlow: Flow[Message, Message, (Future[Seq[Message]], Promise[Option[Message]])] =
        Flow.fromSinkAndSourceMat(Sink.seq[Message], src.concatMat(Source.maybe[Message])(Keep.right))(Keep.both)

      // NB: using the technique mentioned for "half-closed" websockets here to get output
      // even when we are not putting any items in.
      // https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#half-closed-websockets
      //
      val (_, (out, promise)) =
        Http().singleWebSocketRequest(WebSocketRequest(wsUrl, extraHeaders = headers), outFlow)

      // Clear the index so we know we're testing against a clean start
      await(mediator.handle.clearAll())
      await(engine.search(query)).page.size must_== 0

      // Here we can't read any messages till we've signalled the end of the input stream, but in
      // reality the indexer is working behind-the-scenes. So we need to wait for some time.
      // Currently we'd expect X number of items to be returned by a fully-indexed search engine:
      // change this if the fixtures change and you're getting unexpected results!
      // NB: this is the number of items, not descriptions which is what the
      // search engine indexes - there are something like 46 descriptions.
      // Wait up to ten seconds until we get the expected number of items:
      val EXPECTED = 39
      await(engine.search(query)).page.size must be_==(EXPECTED).eventually(100, 100.millis)

      // close the connection...
      promise.success(None)

      val messages = await(out)
      messages.foreach(println)
      messages.size must_== 2
      messages.last must_== jsonMessage(WebsocketConstants.DONE_MESSAGE)
    }
  }
}
