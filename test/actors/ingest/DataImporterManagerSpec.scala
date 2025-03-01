package actors.ingest

import org.apache.pekko.actor.Props
import helpers.IntegrationTestRunner
import models._
import services.data.DataUser
import services.ingest.IngestService.{IngestData, IngestJob}
import services.ingest._
import utils.WebsocketConstants

class DataImporterManagerSpec extends IntegrationTestRunner {

  import mockdata.adminUserProfile

  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private val jobId = "test-job-id"
  private val datasetId = "default"
  private val keyVersion = "foo.ead?versionId=1"
  private val job: IngestJob = IngestJob(jobId,
    List(IngestData(
      IngestParams(
        ContentTypes.Repository,
        scope = "r1",
        log = "Testing...",
        data = UrlMapPayload(Map(keyVersion -> java.net.URI.create("http://example.com/foo.ead")))
      ),
      IngestService.IngestDataType.Ead,
      "application/json",
      DataUser(userOpt.map(_.id)),
      hostInstance,
    )
  ))


  "Data Import manager" should {

    "send correct messages when importing files" in new ITestAppWithPekko {
      val importApi = MockIngestService(ImportLog())
      val importManager = system.actorOf(Props(DataImporterManager(job, importApi)))

      importManager ! self

      expectMsg(s"Initialising ingest for job: $jobId...")
      expectMsg("Data: created: 0, updated: 0, unchanged: 0, errors: 0")
      expectMsg("Task was a dry run so not proceeding to reindex")
      expectMsg("Uploading log...")
      expectMsg("Log stored at http://example.com/log")
      expectMsg(WebsocketConstants.DONE_MESSAGE)
    }

    "send correct messages when imports throw an error" in new ITestAppWithPekko {
      val importApi = MockIngestService(ErrorLog("identifier", "Missing field"))
      val importManager = system.actorOf(Props(DataImporterManager(job, importApi)))

      importManager ! self

      expectMsg(s"Initialising ingest for job: $jobId...")
      expectMsg("Error: identifier: Missing field")
    }
  }
}
