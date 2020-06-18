package actors

import actors.OaiPmhHarvester.{OaiPmhHarvestData, OaiPmhHarvestJob}
import akka.actor.Props
import helpers.{AkkaTestkitSpecs2Support, IntegrationTestRunner}
import mockdata.adminUserProfile
import models.{OaiPmhConfig, UserProfile}
import play.api.{Application, Configuration}
import services.harvesting.OaiPmhClient
import services.storage.FileStorage

class OaiPmhHarvestRunnerSpec extends AkkaTestkitSpecs2Support with IntegrationTestRunner {

  private def client(implicit app: Application): OaiPmhClient = app.injector.instanceOf[OaiPmhClient]
  private def storage(implicit app: Application): FileStorage = app.injector.instanceOf[FileStorage]
  private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]

  private val jobId = "test-job-id"
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private def job(implicit app: Application) = OaiPmhHarvestJob(jobId, "r1", OaiPmhHarvestData(
    // where we're harvesting from:
    config = OaiPmhConfig(s"${utils.serviceBaseUrl("ehridata", config)}/oaipmh", "ead", Some("nl:r1")),
    // on-storage location:
    classifier = "test-bucket",
    prefix = "oaipmh/r1/"
  ))

  import OaiPmhHarvestRunner._

  "OAI-PMH harvest runner" should {

    "send correct messages when harvesting an endpoint" in new ITestApp {
      val runner = system.actorOf(Props(OaiPmhHarvestRunner(job, client, storage)))

      runner ! Initial
      expectMsg(Starting)
      expectMsgAnyOf(DoneFile("c4"), DoneFile("nl-r1-m19"))
      expectMsgAnyOf(DoneFile("c4"), DoneFile("nl-r1-m19"))
      expectMsgClass(classOf[Completed])
    }

    "allow cancellation" in new ITestApp {
      val runner = system.actorOf(Props(OaiPmhHarvestRunner(job, client, storage)))

      runner ! Initial
      expectMsg(Starting)
      expectMsgAnyOf(DoneFile("c4"), DoneFile("nl-r1-m19"))
      runner ! Cancel
      expectMsgClass(classOf[Cancelled])
    }
  }
}
