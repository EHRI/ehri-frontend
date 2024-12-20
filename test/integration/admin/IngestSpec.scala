package integration.admin

import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.google.inject.name.Names
import helpers.{FakeMultipartUpload, IntegrationTestRunner}
import models.{ContentTypes, IngestParams}
import org.apache.commons.io.FileUtils
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.JsString
import play.api.test.FakeRequest
import services.ingest.IngestService
import services.storage.FileStorage

import java.io.File
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}


/**
  * Spec to test the ingest UI and websocket monitoring.
  */
class IngestSpec extends IntegrationTestRunner with FakeMultipartUpload {

  import mockdata.privilegedUser

  private def getEadFile: File = {
    val tmpFile = java.io.File.createTempFile("anEad", ".xml")
    tmpFile.deleteOnExit()
    FileUtils.copyToFile(getClass.getClassLoader.getResourceAsStream("hierarchical-ead.xml"), tmpFile)
    tmpFile
  }


  "Ingest views" should {
    "perform ead-sync and monitor progress correctly" in new ITestServer(app = appBuilder.configure(getConfig).build()) {
      val damStorage = app.injector.instanceOf(BindingKey(classOf[FileStorage], Some(QualifierInstance(Names.named("dam")))))

      val result = FakeRequest(controllers.admin.routes.Ingest
          .ingestPost(ContentTypes.Repository, "r1", IngestService.IngestDataType.EadSync))
        .withFileUpload(IngestParams.DATA_FILE, getEadFile, "text/xml", data = Map(
          IngestParams.SCOPE_TYPE -> Seq(ContentTypes.Repository.toString),
          IngestParams.SCOPE -> Seq("r1"),
          IngestParams.EXCLUDES -> Seq("c1\nc2\nc3\nc4\nnl-r1-m19"),
          IngestParams.LOG -> Seq("test"),
          IngestParams.COMMIT -> Seq("true")
        ))
        .withUser(privilegedUser)
        .withCsrf
        .call()

      status(result) must_== SEE_OTHER
      val loc = redirectLocation(result)
      loc must beSome
      val relativeUrl = loc.get

      val jobId = relativeUrl.split("=")(1)
      val wsUrl = s"ws://127.0.0.1:${this.port}${controllers.admin.routes.Tasks.taskMonitorWS(jobId)}"

      val headers = collection.immutable.Seq(RawHeader(AUTH_TEST_HEADER_NAME, testAuthToken(privilegedUser.id)))
      val outFlow: Flow[Message, Message, (Future[Seq[Message]], Promise[Option[Message]])] =
        Flow.fromSinkAndSourceMat(Sink.seq[Message], Source.maybe[Message])(Keep.both)

      val (_, (out, promise)) =
        Http().singleWebSocketRequest(WebSocketRequest(wsUrl, extraHeaders = headers), outFlow)

      // Repeat this matcher until we have a suitable log, up to 10 seconds
      await(damStorage.listFiles(Some(s"$hostInstance/ingest-logs/")))
        .files.find(_.key.contains(jobId)) must beSome
        .eventually(retries = 50, sleep = 200.millis)

      // close the connection...
      promise.success(None)

      val messages = await(out)
      println(messages)
      messages.head.asTextMessage.getStrictText must contain(jobId)
      messages.collectFirst { case TextMessage.Strict(t) if t.startsWith("\"Event ID:") => t } must beSome
      messages must contain(TextMessage.Strict(JsString("Data: created: 5, updated: 0, unchanged: 0, errors: 0").toString))
      messages must contain(TextMessage.Strict(JsString("Sync: moved: 0, new: 5, deleted: 0").toString))
      messages must contain(TextMessage.Strict(JsString("Creating redirects...").toString))
      messages must contain(TextMessage.Strict(JsString("Remapped 0 item(s)").toString))
      messages must contain(TextMessage.Strict(JsString("Reindexing...").toString))
      messages.collectFirst { case TextMessage.Strict(t) if t.startsWith("\"Log stored at http") => t } must beSome
      messages.last must_== TextMessage.Strict(JsString(utils.WebsocketConstants.DONE_MESSAGE).toString)

    }
  }
}
