package actors.harvesting

import actors.LongRunningJob.Cancel
import actors.harvesting
import actors.harvesting.ResourceSyncHarvester.{ResourceSyncData, ResourceSyncJob}
import org.apache.pekko.actor.Props
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models.{ResourceSyncConfig, UserProfile}
import play.api.Application
import services.harvesting.ResourceSyncClient
import services.storage.FileStorage

class ResourceSyncHarvesterSpec extends IntegrationTestRunner {

  // NB: instantiate a *mock* ResourceSync client here:
  private def client(implicit app: Application): ResourceSyncClient = app.injector.instanceOf[ResourceSyncClient]

  private def storage(implicit app: Application): FileStorage = app.injector.instanceOf[FileStorage]

  private val jobId = "test-job-id"
  private val datasetId = "default"
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private def job(implicit app: Application): ResourceSyncJob = ResourceSyncJob("r1", datasetId, jobId, ResourceSyncData(
    // where we're harvesting from:
    config = ResourceSyncConfig(s"https://example.com/resourcesync/capabilitylist.xml"),
    prefix = "r1/input/"
  ))

  import Harvester._

  "ResourceSync harvest" should {

    "send correct messages when syncing a capabilitylist" in new ITestApp {
      new TestKit(system) with ImplicitSender {
        val runner = system.actorOf(Props(ResourceSyncHarvester(client, storage)))

        runner ! job
        expectMsg(Starting)
        expectMsg(ToDo(3))
        expectMsg(DoneFile("+ test1.xml"))
        expectMsg(DoneFile("+ test2.xml"))
        expectMsg(DoneFile("+ test3.xml"))
        expectMsgClass(classOf[Completed])
      }
    }

    "allow cancellation" in new ITestApp {
      new TestKit(system) with ImplicitSender {
        val runner = system.actorOf(Props(harvesting.ResourceSyncHarvester(client, storage)))

        // NB: this test is slightly non-deterministic in that the first `ToDo` can
        // sometimes arrive before cancellation is processed...
        runner ! job
        expectMsg(Starting)
        runner ! Cancel
        expectMsgClass(classOf[Cancelled])
      }
    }
  }
}
