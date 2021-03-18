package actors.harvesting

import actors.harvesting
import actors.harvesting.ResourceSyncHarvesterManager.{ResourceSyncData, ResourceSyncJob}
import akka.actor.Props
import akka.testkit.{ImplicitSender, TestKit}
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models.{ResourceSyncConfig, UserProfile}
import play.api.Application
import services.harvesting.ResourceSyncClient
import services.storage.FileStorage

class ResourceSyncHarvesterSpec extends IntegrationTestRunner {

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

  import ResourceSyncHarvester._

  "ResourceSync harvest" should {

    "send correct messages when syncing a capabilitylist" in new ITestApp {
      new TestKit(implicitActorSystem) with ImplicitSender {
        val runner = system.actorOf(Props(ResourceSyncHarvester(job, client, storage)))

        runner ! Initial
        expectMsg(Starting)
        expectMsg(ToDo(3))
        expectMsg(DoneFile("+ test1.xml"))
        expectMsg(DoneFile("+ test2.xml"))
        expectMsg(DoneFile("+ test2.xml"))
        expectMsgClass(classOf[Completed])
      }
    }

    "allow cancellation" in new ITestApp {
      new TestKit(implicitActorSystem) with ImplicitSender {
        val runner = system.actorOf(Props(harvesting.ResourceSyncHarvester(job, client, storage)))

        // NB: this test is slightly non-deterministic in that the first `ToDo` can
        // sometimes arrive before cancellation is processed...
        runner ! Initial
        expectMsg(Starting)
        runner ! Cancel
        expectMsgClass(classOf[Cancelled])
      }
    }
  }
}
