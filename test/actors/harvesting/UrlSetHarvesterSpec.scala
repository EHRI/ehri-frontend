package actors.harvesting

import actors.LongRunningJob.Cancel
import actors.harvesting
import actors.harvesting.UrlSetHarvester.{UrlSetHarvesterData, UrlSetHarvesterJob}
import org.apache.pekko.actor.Props
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models.{BasicAuthConfig, UrlSetConfig, UserProfile}
import play.api.libs.ws.WSClient
import play.api.{Application, Configuration}
import services.ServiceConfig
import services.storage.FileStorage

class UrlSetHarvesterSpec extends IntegrationTestRunner {

  private def client(implicit app: Application): WSClient = app.injector.instanceOf[WSClient]
  private def storage(implicit app: Application): FileStorage = app.injector.instanceOf[FileStorage]
  private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]

  private val jobId = "test-job-id"
  private val datasetId = "default"
  private val itemIds = Seq("c4", "nl-r1-m19")
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private def job(implicit app: Application) = {
    val serviceConfig = ServiceConfig("ehridata", config)
    val urls = itemIds.map(id => s"${serviceConfig.baseUrl}/classes/DocumentaryUnit/$id/ead" -> s"$id.xml")
    UrlSetHarvesterJob("r1", datasetId, jobId, UrlSetHarvesterData(
      // the URLs we're harvesting and the server auth
      config = UrlSetConfig(urls, auth = serviceConfig.credentials.map { case (u, pw) => BasicAuthConfig(u, pw) }),
      prefix = "urlset/r1/"
    ))
  }

  private def postJob(implicit app: Application) = {
    // harvest the OAI-PMH endpoint as a test, since it supports POST
    val serviceConfig = ServiceConfig("ehridata", config)
    val urls = Seq(s"${serviceConfig.baseUrl}/oaipmh" -> s"test.xml")
    UrlSetHarvesterJob("r1", datasetId, jobId, UrlSetHarvesterData(
      config = UrlSetConfig(urls, method = "POST", auth = serviceConfig.credentials.map { case (u, pw) => BasicAuthConfig(u, pw)}),
      prefix = "urlset/r1/"
    ))
  }

  import Harvester._

  "URL set harvest runner" should {

    "send correct messages when harvesting an endpoint" in new ITestAppWithPekko {
      val runner = system.actorOf(Props(UrlSetHarvester(client, storage)))

      runner ! job
      expectMsg(Starting)
      expectMsg(ToDo(2))
      expectMsg(DoneFile("+ c4.xml"))
      expectMsg(DoneFile("+ nl-r1-m19.xml"))
      expectMsgClass(classOf[Completed])
    }

    "allow cancellation" in new ITestAppWithPekko {
      val runner = system.actorOf(Props(harvesting.UrlSetHarvester(client, storage)))

      runner ! job
      expectMsg(Starting)
      expectMsg(ToDo(2))
      expectMsg(DoneFile("+ c4.xml"))
      runner ! Cancel
      expectMsgClass(classOf[Cancelled])
    }

    "support POST requests to endpoints" in new ITestAppWithPekko {
      val runner = system.actorOf(Props(UrlSetHarvester(client, storage)))

      runner ! postJob

      expectMsg(Starting)
      expectMsg(ToDo(1))
      expectMsg(DoneFile("+ test.xml"))
      expectMsgClass(classOf[Completed])
    }
  }
}
