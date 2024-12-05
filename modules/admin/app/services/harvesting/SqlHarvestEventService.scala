package services.harvesting

import java.io.{PrintWriter, StringWriter}

import org.apache.pekko.actor.ActorSystem
import anorm.{Macro, RowParser, _}
import javax.inject.Inject
import models.{HarvestEvent, UserProfile}
import models.HarvestEvent.HarvestEventType
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}

case class SqlHarvestEventService @Inject()(db: Database, actorSystem: ActorSystem) extends HarvestEventService {
  private implicit def ec: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[HarvestEvent] =
    Macro.parser[HarvestEvent](
      "repo_id", "import_dataset_id", "job_id", "user_id", "event_type", "info", "created")

  override def get(repoId: String, datasetId: Option[String] = None, jobId: Option[String] = None): Future[Seq[HarvestEvent]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
         SELECT * FROM harvest_event
         WHERE repo_id = $repoId
           AND ($datasetId IS NULL OR import_dataset_id = $datasetId)
           AND ($jobId IS NULL OR job_id = $jobId)
         ORDER BY created ASC
         """.as(parser.*)
    }
  }(ec)

  override def save(repoId: String, datasetId: String, jobId: String, info: Option[String] = None)(
      implicit userOpt: Option[UserProfile]): Future[HarvestEventHandle] = Future {
    saveEvent(repoId, datasetId, jobId, HarvestEventType.Started, info, userOpt)
    new HarvestEventHandle {
      override def close(): Future[Unit] = Future {
        saveEvent(repoId, datasetId, jobId, HarvestEventType.Completed, Option.empty, userOpt)
      }(ec)

      override def cancel(): Future[Unit] = Future {
        saveEvent(repoId, datasetId, jobId, HarvestEventType.Cancelled, Option.empty, userOpt)
      }(ec)

      override def error(t: Throwable): Future[Unit] = Future {
        saveEvent(repoId, datasetId, jobId, HarvestEventType.Errored, Some(stackTrace(t)), userOpt)
      }(ec)

      private def stackTrace(e: Throwable): String = {
        val sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        sw.toString
      }
    }
  }(ec)

  private def saveEvent(repoId: String,
                        datasetId: String,
                        jobId: String,
                        eventType: HarvestEventType.Value,
                        info: Option[String],
                        userOpt: Option[UserProfile]): Unit = {
    db.withConnection { implicit conn =>
      SQL"""
         INSERT INTO harvest_event
            (repo_id, import_dataset_id, job_id, user_id, event_type, info)
         VALUES (
            $repoId,
            $datasetId,
            $jobId,
            ${userOpt.map(_.id)},
            $eventType,
            $info
         )""".executeInsert()
      ()
    }
  }
}
