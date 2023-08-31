package actors.cleanup

import actors.LongRunningJob
import actors.cleanup.CleanupRunner.CleanupJob
import akka.actor.{ActorContext, ActorRef, Props}
import com.google.inject.name.Names
import controllers.datasets.{CleanupConfirmation, LongRunningJobs}
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models._
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.{Application, Configuration}
import services.data.DataUser
import services.ingest.{ImportLogService, IngestService}


class CleanupRunnerManagerSpec extends IntegrationTestRunner {

  implicit private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]
  private def logService(implicit app: Application): ImportLogService = app.injector.instanceOf[ImportLogService]
  private def importService(implicit app: Application): IngestService = app.injector.instanceOf[IngestService]
  private def eventForwarder(implicit app: Application): ActorRef = app.injector.instanceOf[ActorRef](
    BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("event-forwarder")))))
  implicit def messagesApi(implicit app: Application): MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit def messages(implicit app: Application): Messages = messagesApi.preferred(Seq(Lang.defaultLang))


  private val jobId = "test-job-id"
  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)
  implicit val apiUser: DataUser = DataUser(userOpt.map(_.id))

  "Harvester Manager" should {

    "send correct messages when running a cleanup job" in new DBTestAppWithAkka("import-log-fixture.sql",
        specificConfig = Map("ehri.admin.bulkOperations.maxDeletions" -> 1)) {
      val cleanupConfirmation = CleanupConfirmation("Delete it")
      val cleanupJob: CleanupJob = CleanupJob("r1", 1, jobId, cleanupConfirmation.msg)

      val init = (context: ActorContext) => context.actorOf(Props(CleanupRunner(dataApi, logService, importService, eventForwarder)))
      val manager = system.actorOf(Props(CleanupRunnerManager(cleanupJob, init)))

      manager ! self // initial subscriber should start harvesting
      expectMsg(s"Starting cleanup with job id: $jobId")
      expectMsg("1 item to move")
      expectMsg("2 items to delete")
      expectMsg("Updated links and annotations")
      expectMsg("Redirected 1 item")
      expectMsg("Deleted 1 item")
      expectMsg("Deleted 2 items")
      expectMsg("Cleanup complete")
      expectMsg("Done")
    }

    "be cancellable" in new DBTestAppWithAkka("import-log-fixture.sql") {
      val cleanupConfirmation = CleanupConfirmation("Delete it")
      val cleanupJob: CleanupJob = CleanupJob("r1", 1, jobId, cleanupConfirmation.msg)

      val init = (context: ActorContext) => context.actorOf(Props(CleanupRunner(dataApi, logService, importService, eventForwarder)))
      val manager = system.actorOf(Props(CleanupRunnerManager(cleanupJob, init)))

      manager ! self // initial subscriber should start harvesting
      expectMsg(s"Starting cleanup with job id: $jobId")
      manager ! LongRunningJob.Cancel
      expectMsg("Cancelled")

    }
  }
}
