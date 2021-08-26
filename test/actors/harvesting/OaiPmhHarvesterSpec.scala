package actors.harvesting

import actors.LongRunningJob.Cancel
import actors.harvesting
import actors.harvesting.OaiPmhHarvester.{OaiPmhHarvestData, OaiPmhHarvestJob}
import akka.actor.Props
import config.serviceBaseUrl
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models.{OaiPmhConfig, UserProfile}
import play.api.{Application, Configuration}
import services.harvesting.OaiPmhClient
import services.storage.FileStorage

class OaiPmhHarvesterSpec extends IntegrationTestRunner {

  private def client(implicit app: Application): OaiPmhClient = app.injector.instanceOf[OaiPmhClient]
  private def storage(implicit app: Application): FileStorage = app.injector.instanceOf[FileStorage]
  private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]

  private val jobId = "test-job-id"
  private val datasetId = "default"
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private def job(implicit app: Application) = OaiPmhHarvestJob("r1", datasetId, jobId, OaiPmhHarvestData(
    // where we're harvesting from:
    config = OaiPmhConfig(s"${serviceBaseUrl("ehridata", config)}/oaipmh", "ead", Some("nl:r1")),
    prefix = "oaipmh/r1/"
  ))

  import Harvester._

  "OAI-PMH harvest runner" should {

    "send correct messages when harvesting an endpoint" in new ITestAppWithAkka {
      val runner = system.actorOf(Props(OaiPmhHarvester(client, storage)))

      runner ! job
      expectMsg(Starting)
      expectMsgAnyOf(DoneFile("c4"), DoneFile("nl-r1-m19"))
      expectMsgAnyOf(DoneFile("c4"), DoneFile("nl-r1-m19"))
      expectMsgClass(classOf[Completed])
    }

    "allow cancellation" in new ITestAppWithAkka {
      val runner = system.actorOf(Props(harvesting.OaiPmhHarvester(client, storage)))

      runner ! job
      expectMsg(Starting)
      expectMsgAnyOf(DoneFile("c4"), DoneFile("nl-r1-m19"))
      runner ! Cancel
      expectMsgClass(classOf[Cancelled])
    }
  }
}
