package actors.harvesting

import actors.harvesting
import actors.harvesting.Harvester.Cancel
import actors.harvesting.OaiPmhHarvester.{OaiPmhHarvestData, OaiPmhHarvestJob}
import actors.harvesting.ResourceSyncHarvester.{ResourceSyncData, ResourceSyncJob}
import akka.actor.{ActorContext, Props}
import config.serviceBaseUrl
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models.HarvestEvent.HarvestEventType
import models.{HarvestEvent, OaiPmhConfig, ResourceSyncConfig, UserProfile}
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.{Application, Configuration}
import services.harvesting.{MockHarvestEventService, OaiPmhClient, ResourceSyncClient}
import services.storage.FileStorage
import utils.WebsocketConstants

import java.time.Instant
import java.time.format.DateTimeFormatter


class HarvesterManagerSpec extends IntegrationTestRunner {

  private def oaiPmhClient(implicit app: Application): OaiPmhClient = app.injector.instanceOf[OaiPmhClient]
  private def rsClient(implicit app: Application): ResourceSyncClient = app.injector.instanceOf[ResourceSyncClient]
  private def storage(implicit app: Application): FileStorage = app.injector.instanceOf[FileStorage]
  private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]
  implicit def messagesApi(implicit app: Application): MessagesApi = app.injector.instanceOf[MessagesApi]

  implicit def messages(implicit app: Application): Messages = messagesApi.preferred(Seq(Lang.defaultLang))

  private val jobId = "test-job-id"
  private val datasetId = "default"
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private def oaiPmhJob(implicit app: Application) = OaiPmhHarvestJob("r1", datasetId, jobId, OaiPmhHarvestData(
    // where we're harvesting from:
    config = OaiPmhConfig(s"${serviceBaseUrl("ehridata", config)}/oaipmh", "ead", Some("nl:r1")),
    prefix = "oaipmh/r1/"
  ))

  private def rsJob(implicit app: Application): ResourceSyncJob = ResourceSyncJob("r1", datasetId, jobId, ResourceSyncData(
    // where we're harvesting from:
    config = ResourceSyncConfig(s"https://example.com/resourcesync/capabilitylist.xml"),
    prefix = "r1/input/"
  ))


  "OAI-PMH harvester" should {
    import scala.concurrent.duration._

    "send correct messages when harvesting an endpoint via OAI-PMH" in new ITestAppWithAkka {
      val events = MockHarvestEventService()
      val init = (context: ActorContext) => context.actorOf(Props(OaiPmhHarvester(oaiPmhClient, storage)))
      val harvester = system.actorOf(Props(HarvesterManager(oaiPmhJob, init, events)))

      harvester ! self // initial subscriber should start harvesting
      expectMsg(s"Starting harvest with job id: $jobId")
      expectMsg(s"Harvesting from earliest date")
      expectMsgAnyOf("c4", "nl-r1-m19")
      expectMsgAnyOf("c4", "nl-r1-m19")
      val msg: String = receiveOne(5.seconds).asInstanceOf[String]
      msg must startWith(s"${WebsocketConstants.DONE_MESSAGE}: synced 2 new files")
      await(events.get("r1")).lift(1) must beSome[HarvestEvent]
        .which(_.eventType must_== HarvestEventType.Completed)
        .eventually(20, 100.millis)
    }

    "send correct messages when harvesting an endpoint via ResourceSync" in new ITestAppWithAkka {
        val events = MockHarvestEventService()
        val init = (context: ActorContext) => context.actorOf(Props(ResourceSyncHarvester(rsClient, storage)))
        val harvester = system.actorOf(Props(HarvesterManager(rsJob, init, events)))

        harvester ! self // initial subscriber should start harvesting
        expectMsg(s"Starting harvest with job id: $jobId")
        expectMsg(s"Syncing 3 files")
        expectMsg("+ test1.xml")
        expectMsg("+ test2.xml")
        expectMsg("+ test3.xml")
        val msg: String = receiveOne(5.seconds).asInstanceOf[String]
        msg must startWith(s"${WebsocketConstants.DONE_MESSAGE}: synced 3 new files")
        await(events.get("r1")).lift(1) must beSome[HarvestEvent]
          .which(_.eventType must_== HarvestEventType.Completed)
          .eventually(20, 100.millis)
    }

    "handle errors" in new ITestAppWithAkka {
      val events = MockHarvestEventService()
      val harvestConf = oaiPmhJob.copy(data = oaiPmhJob.data.copy(config = oaiPmhJob.data.config.copy(url = "http://example.com/oaipmh")))
      val init = (context: ActorContext) => context.actorOf(Props(OaiPmhHarvester(oaiPmhClient, storage)))
      val harvester = system.actorOf(Props(HarvesterManager(harvestConf, init, events)))

      harvester ! self // initial subscriber should start harvesting
      expectMsg(s"Starting harvest with job id: $jobId")
      expectMsg(s"Harvesting from earliest date")
      expectMsg(20.seconds, s"${WebsocketConstants.ERR_MESSAGE}: ${Messages("oaipmh.error.url")}")

      // If there's an error before anything is harvested we don't log anything
      await(events.get("r1")) must_== List.empty[HarvestEvent]
    }

    "harvest selectively with `from` date" in new ITestAppWithAkka {
        val events = MockHarvestEventService()
        val start: Instant = Instant.now()
        val job2 = oaiPmhJob(app)
        val dateJob = job2.copy(data = job2.data.copy(from = Some(start)))
        val init = (context: ActorContext) => context.actorOf(Props(OaiPmhHarvester(oaiPmhClient, storage)))
        val harvester = system.actorOf(Props(HarvesterManager(dateJob, init, events)))

        harvester ! self // initial subscriber should start harvesting
        expectMsg(s"Starting harvest with job id: $jobId")
        expectMsg(s"Harvesting from ${DateTimeFormatter.ISO_INSTANT.format(start)}")
        expectMsg("Done: nothing new to sync")
        await(events.get("r1")) must_== List.empty[HarvestEvent]
    }

    "cancel jobs" in new ITestAppWithAkka {
        val events = MockHarvestEventService()
        val init = (context: ActorContext) => context.actorOf(Props(OaiPmhHarvester(oaiPmhClient, storage)))
        val harvester = system.actorOf(Props(harvesting.HarvesterManager(oaiPmhJob, init, events)))

        harvester ! self // initial subscriber should start harvesting
        expectMsg(s"Starting harvest with job id: $jobId")
        expectMsg(s"Harvesting from earliest date")
        expectMsgAnyOf("c4", "nl-r1-m19")
        await(events.get("r1")).head.eventType must_== HarvestEventType.Started

        harvester ! Cancel

        val msg: String = receiveOne(5.seconds).asInstanceOf[String]
        msg must startWith(s"${WebsocketConstants.ERR_MESSAGE}: cancelled after")

        // Wait up to a minute for the expected events to appear
        await(events.get("r1")).find(_.eventType == HarvestEventType.Cancelled) must beSome[HarvestEvent]
          .eventually(retries = 300, sleep = 200.millis)
    }
  }
}
