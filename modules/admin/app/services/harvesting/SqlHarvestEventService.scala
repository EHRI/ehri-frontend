package services.harvesting

import akka.actor.ActorSystem
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
      "repo_id", "job_id", "user_id", "event_type", "info", "created")



  override def get(repoId: String): Future[Seq[HarvestEvent]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
           SELECT * FROM harvest_event
           WHERE repo_id = $repoId
           ORDER BY created ASC""".as(parser.*)
    }
  }(ec)

  override def get(repoId: String, jobId: String): Future[Seq[HarvestEvent]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
         SELECT * FROM harvest_event
         WHERE repo_id = $repoId AND job_id = $jobId
         ORDER BY created ASC""".as(parser.*)
    }
  }(ec)

  override def save(repoId: String, jobId: String, eventType: HarvestEventType.Value, info: Option[String] = None)(
      implicit userOpt: Option[UserProfile]): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL"""
         INSERT INTO harvest_event
            (repo_id, job_id, user_id, event_type, info)
         VALUES (
            $repoId,
            $jobId,
            ${userOpt.map(_.id)},
            $eventType,
            $info
         )""".executeInsert()
      ()
    }
  }(ec)

}
