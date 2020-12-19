package actors.harvesting

import java.time.Instant
import java.time.format.DateTimeFormatter

import actors.harvesting
import actors.harvesting.OaiPmhHarvester.Cancel
import actors.harvesting.OaiPmhHarvesterManager.{OaiPmhHarvestData, OaiPmhHarvestJob}
import akka.actor.Props
import helpers.{AkkaTestkitSpecs2Support, IntegrationTestRunner}
import mockdata.adminUserProfile
import models.HarvestEvent.HarvestEventType
import models.{OaiPmhConfig, UserProfile}
import play.api.{Application, Configuration}
import services.harvesting.{MockHarvestEventService, OaiPmhClient}
import services.storage.FileStorage
import utils.WebsocketConstants


class OaiPmhHarvesterManagerSpec extends AkkaTestkitSpecs2Support with IntegrationTestRunner {

  private def client(implicit app: Application): OaiPmhClient = app.injector.instanceOf[OaiPmhClient]
  private def storage(implicit app: Application): FileStorage = app.injector.instanceOf[FileStorage]
  private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]

  private val jobId = "test-job-id"
  private val datasetId = "default"
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private def job(implicit app: Application) = OaiPmhHarvestJob("r1", datasetId, jobId, OaiPmhHarvestData(
    // where we're harvesting from:
    config = OaiPmhConfig(s"${utils.serviceBaseUrl("ehridata", config)}/oaipmh", "ead", Some("nl:r1")),
    // on-storage location:
    classifier = "test-bucket",
    prefix = "oaipmh/r1/"
  ))

  "OAI-PMH harvester" should {
    import scala.concurrent.duration._

    "send correct messages when harvesting an endpoint" in new ITestApp {
      val events = MockHarvestEventService()
      val harvester = system.actorOf(Props(OaiPmhHarvesterManager(job, client, storage, events)))

      harvester ! self // initial subscriber should start harvesting
      expectMsg(s"Starting harvest with job id: $jobId")
      expectMsg(s"Harvesting from earliest date")
      expectMsgAnyOf("c4", "nl-r1-m19")
      expectMsgAnyOf("c4", "nl-r1-m19")
      val msg: String = receiveOne(5.seconds).asInstanceOf[String]
      msg must startWith(s"${WebsocketConstants.DONE_MESSAGE}: harvested 2 file(s)")
      events.events.lift(1) must beSome.which(_.eventType must_== HarvestEventType.Completed)
    }

    "harvest selectively with `from` date" in new ITestApp {
      val events = MockHarvestEventService()
      val now: Instant = Instant.now()
      val job2 = job(app)
      val dateJob = job2.copy(data = job2.data.copy(from = Some(now)))
      val harvester = system.actorOf(Props(OaiPmhHarvesterManager(dateJob, client, storage, events)))

      harvester ! self // initial subscriber should start harvesting
      expectMsg(s"Starting harvest with job id: $jobId")
      expectMsg(s"Harvesting from ${DateTimeFormatter.ISO_INSTANT.format(now)}")
      expectMsg("Done: nothing to harvest")
      events.events.size must_== 0
    }

    "cancel jobs" in new ITestApp {
      val events = MockHarvestEventService()
      val harvester = system.actorOf(Props(harvesting.OaiPmhHarvesterManager(job, client, storage, events)))

      harvester ! self // initial subscriber should start harvesting
      expectMsg(s"Starting harvest with job id: $jobId")
      expectMsg(s"Harvesting from earliest date")
      expectMsgAnyOf("c4", "nl-r1-m19")
      harvester ! Cancel
      val msg: String = receiveOne(5.seconds).asInstanceOf[String]
      msg must startWith(s"${WebsocketConstants.ERR_MESSAGE}: cancelled after")
      events.events.head.eventType must_== HarvestEventType.Started
      events.events.lift(1) must beSome.which(_.eventType must_== HarvestEventType.Cancelled)
    }
  }
}
