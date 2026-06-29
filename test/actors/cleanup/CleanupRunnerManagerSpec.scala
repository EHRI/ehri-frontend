package actors.cleanup

import actors.LongRunningJob
import actors.cleanup.CleanupRunner.CleanupJob
import controllers.datasets.CleanupConfirmation
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models._
import org.apache.pekko.actor.{ActorContext, Props}
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.{Application, Configuration}
import services.data.DataUser
import services.ingest.{ImportLogService, IngestService}


class CleanupRunnerManagerSpec extends IntegrationTestRunner {

  implicit private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]
  private def logService(implicit app: Application): ImportLogService = app.injector.instanceOf[ImportLogService]
  private def importService(implicit app: Application): IngestService = app.injector.instanceOf[IngestService]
  implicit def messagesApi(implicit app: Application): MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit def messages(implicit app: Application): Messages = messagesApi.preferred(Seq(Lang.defaultLang))


  private val jobId = "test-job-id"
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)
  implicit val apiUser: DataUser = DataUser(userOpt.map(_.id))

  "Cleanup Manager" should {

    "send correct messages when running a cleanup job" in new DBTestAppWithPekko("import-log-fixtures.sql",
        specificConfig = Map("ehri.admin.bulkOperations.maxDeletions" -> 1)) {

      // To test this properly we need to create the item referenced in the fixtures 'nl-r1-TEST-m19':
      await(dataApi.createInContext[Repository, DocumentaryUnitF, DocumentaryUnit]("r1", DocumentaryUnitF(identifier = "test")))
      val before = await(dataApi.createInContext[DocumentaryUnit, DocumentaryUnitF, DocumentaryUnit](
        "nl-r1-test", DocumentaryUnitF(identifier = "m19")))
      before.pid must beSome.which(pid => pid must_!== "m19-12345678" )

      val cleanupConfirmation = CleanupConfirmation("Delete it")
      val cleanupJob: CleanupJob = CleanupJob("r1", 1, jobId, cleanupConfirmation.msg)

      val init = (context: ActorContext) => context.actorOf(Props(CleanupRunner(dataApi, logService, importService)))
      val manager = system.actorOf(Props(CleanupRunnerManager(cleanupJob, init)))

      manager ! self // initial subscriber should start harvesting
      expectMsg(s"Starting cleanup with job id: $jobId")
      expectMsg("1 item to move")
      expectMsg("2 items to delete")
      expectMsg("Updated links and annotations for 1 item")
      expectMsg("Created 2 redirects")
      expectMsg("Deleted 1 item")
      expectMsg("Deleted 2 items")
      expectMsg("Cleanup complete")
      expectMsg("Done")

      val after = await(dataApi.get[DocumentaryUnit]("nl-r1-test-m19"))
      after.pid must beSome("m19-12345678")
    }

    "be cancellable" in new DBTestAppWithPekko("import-log-fixtures.sql") {
      val cleanupConfirmation = CleanupConfirmation("Delete it")
      val cleanupJob: CleanupJob = CleanupJob("r1", 1, jobId, cleanupConfirmation.msg)

      val init = (context: ActorContext) => context.actorOf(Props(CleanupRunner(dataApi, logService, importService)))
      val manager = system.actorOf(Props(CleanupRunnerManager(cleanupJob, init)))

      manager ! self // initial subscriber should start harvesting
      expectMsg(s"Starting cleanup with job id: $jobId")
      manager ! LongRunningJob.Cancel
      expectMsg("Cancelled")

    }
  }
}
