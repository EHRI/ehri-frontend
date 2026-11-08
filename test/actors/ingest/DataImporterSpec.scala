package actors.ingest

import actors.ingest.DataImporter.{Done, Message, Start, UnexpectedError}
import org.apache.pekko.actor.{ActorRef, Props}
import helpers.IntegrationTestRunner
import models._
import services.data.DataUser
import services.ingest.IngestService.{IngestData, IngestJob}
import services.ingest._

import scala.concurrent.Future

class DataImporterSpec extends IntegrationTestRunner {

  import mockdata.adminUserProfile

  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)
  private val payload = Map(
    "foo.ead?version=1" -> java.net.URI.create("http://example.com/foo.ead"),
    "bar.ead?version=1" -> java.net.URI.create("http://example.com/bar.ead"))
  private val jobId = "test-job-id"
  private val ingestData = IngestData(
    IngestParams(
      ContentTypes.Repository,
      scope = "r1",
      log = "Testing...",
      data = UrlMapPayload(payload)
    ),
    IngestService.IngestDataType.Ead,
    "application/json",
    DataUser(userOpt.map(_.id)),
    hostInstance,
  )
  private val job: IngestJob = IngestJob(jobId, List(ingestData))


  "Data Importer" should {
    "send correct messages when importing files" in new ITestAppWithPekko {
      val importLog = ImportLog()
      val importApi = MockIngestService(importLog)
      val importer = system.actorOf(Props(DataImporter(job, importApi, (_, _) => Future.successful(()))))

      importer ! Start

      expectMsg(Message(s"Initialising ingest for job: $jobId..."))
      expectMsg(importLog)
      expectMsg(Message("Data: created: 0, updated: 0, unchanged: 0, errors: 0"))
      expectMsg(Message("Uploading log..."))
      expectMsg(Message("Log stored at http://example.com/log"))
      expectMsg(Message("Task was a dry run so not proceeding to reindex"))
      expectMsgType[Done]
    }

    "send correct messages when batch importing files" in new ITestAppWithPekko {
      val batchData = payload.toSeq.zipWithIndex.map { case ((k, v), i) =>
        ingestData.copy(params = ingestData.params.copy(data = UrlMapPayload(Map(k -> v))), batch = Some(i + 1))
      }.toList
      val batchJob = job.copy(data = batchData, batchSize = Some(1))
      val importLog = ImportLog()
      val importApi = MockIngestService(importLog)
      val importer = system.actorOf(Props(DataImporter(batchJob, importApi, (_, _) => Future.successful(()))))

      importer ! Start

      expectMsg(Message(s"Initialising ingest for job: $jobId..."))
      expectMsg(Message("Splitting job into 2 batches of 1"))
      expectMsg(Message("Starting batch 1"))
      expectMsg(importLog)
      expectMsg(Message("Data: created: 0, updated: 0, unchanged: 0, errors: 0"))
      expectMsg(Message("Uploading log..."))
      expectMsg(Message("Log stored at http://example.com/log"))
      expectMsg(Message("Task was a dry run so not proceeding to reindex"))
      expectMsg(Message("Starting batch 2"))
      expectMsg(importLog)
      expectMsg(Message("Data: created: 0, updated: 0, unchanged: 0, errors: 0"))
      expectMsg(Message("Uploading log..."))
      expectMsg(Message("Log stored at http://example.com/log"))
      expectMsg(Message("Task was a dry run so not proceeding to reindex"))
      expectMsgType[Done]
    }

    "save the log when reindexing fails" in new ITestAppWithPekko {
      val importLog = ImportLog(createdKeys = Map("foo.ead?version=1" -> Seq("c1")))
      val importApi = new MockIngestService(importLog) {
        override def reindex(ids: Seq[String], chan: ActorRef) = Future.failed(new Exception("Solr down"))
      }
      var saved = Option.empty[ImportLog]
      val commitJob = job.copy(data = List(ingestData.copy(params = ingestData.params.copy(commit = true))))
      val importer = system.actorOf(Props(DataImporter(commitJob, importApi, (_, log) => { saved = Some(log); Future.successful(()) })))

      importer ! Start

      expectMsg(Message(s"Initialising ingest for job: $jobId..."))
      expectMsg(importLog)
      expectMsg(Message("Data: created: 1, updated: 0, unchanged: 0, errors: 0"))
      expectMsg(Message("Uploading log..."))
      expectMsg(Message("Log stored at http://example.com/log"))
      expectMsg(Message("Reindexing..."))
      expectMsgType[UnexpectedError].throwable.getMessage must endWith("Solr down")
      saved must beSome(importLog)
    }

    "send correct messages when imports throw an error" in new ITestAppWithPekko {
      val importApi = MockIngestService(ErrorLog("identifier", "Missing field"))
      val importer = system.actorOf(Props(DataImporter(job, importApi, (_, _) => Future.successful(()))))

      importer ! Start

      expectMsg(Message(s"Initialising ingest for job: $jobId..."))
      expectMsg(ErrorLog("identifier", "Missing field"))
    }
  }
}
