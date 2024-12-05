package actors.datamodel

import actors.LongRunningJob.Cancel
import actors.datamodel.Auditor.RunAudit
import actors.datamodel.AuditorManager.{AuditTask, AuditorJob}
import org.apache.pekko.actor.Props
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models.{EntityType, FieldMetadata, FieldMetadataSet, UserProfile}
import play.api.Application
import services.search.{SearchEngine, SearchItemResolver}

import scala.collection.immutable.ListMap


/**
  * This spec runs an audit against all DocumentaryUnits for the
  * mandatory field "locationOfOriginals" and tests the auditor
  * actor gives the correct responses.
  */
class AuditorSpec extends IntegrationTestRunner {

  private def searchEngine(implicit app: Application) = app.injector.instanceOf[SearchEngine]
  private def resolver(implicit app: Application) = app.injector.instanceOf[SearchItemResolver]

  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private val fieldMetadataSet: FieldMetadataSet = FieldMetadataSet(
    fieldMetadata = ListMap(
      "locationOfOriginals" -> FieldMetadata(
        entityType = EntityType.DocumentaryUnit,
        "locationOfOriginals",
        "Location of Originals",
        usage = Some(FieldMetadata.Usage.Mandatory),
      )
    )
  )

  private def job(implicit app: Application): AuditorJob = AuditorJob(
    "test-job-id",
    AuditTask(
      EntityType.DocumentaryUnit,
      None,
      mandatoryOnly = true
    )
  )

  "Auditor runner" should {

    "send correct messages when auditing an entity type" in new ITestAppWithPekko {
      val runner = system.actorOf(Props(Auditor(searchEngine, resolver, fieldMetadataSet, 5, 10)))

      runner ! RunAudit(job, None)
      expectMsg(Auditor.Checked(0))
      expectMsgClass(classOf[Auditor.CheckBatch])
      expectMsgClass(classOf[Auditor.Completed])
    }

    "allow cancellation" in new ITestAppWithPekko {
      val runner = system.actorOf(Props(Auditor(searchEngine, resolver, fieldMetadataSet, 5, 10)))

      runner ! RunAudit(job, None)
      runner ! Cancel
      expectMsgClass(classOf[Auditor.Cancelled])
    }
  }
}
