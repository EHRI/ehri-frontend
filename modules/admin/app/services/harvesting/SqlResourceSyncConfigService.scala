package services.harvesting

import akka.actor.ActorSystem
import anorm.{Macro, RowParser, _}
import javax.inject.{Inject, Singleton}
import models.ResourceSyncConfig
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlResourceSyncConfigService @Inject()(db: Database, actorSystem: ActorSystem) extends ResourceSyncConfigService {

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[ResourceSyncConfig] =
    Macro.parser[ResourceSyncConfig]("endpoint_url", "filter_spec")

  override def get(id: String, ds: String): Future[Option[ResourceSyncConfig]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM resourcesync_config WHERE repo_id = $id AND import_dataset_id = $ds"
        .as(parser.singleOpt)
    }
  }

  override def delete(id: String, ds: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM resourcesync_config WHERE repo_id = $id AND import_dataset_id = $ds".executeUpdate() == 1
    }
  }

  override def save(id: String, ds: String, data: ResourceSyncConfig): Future[ResourceSyncConfig] = Future {
    db.withTransaction { implicit conn =>
      SQL"""INSERT INTO resourcesync_config
        (repo_id, import_dataset_id, endpoint_url, filter_spec)
        VALUES (
          $id,
          $ds,
          ${data.url},
          ${data.filter}
      ) ON CONFLICT (repo_id, import_dataset_id) DO UPDATE
        SET
          endpoint_url = ${data.url},
          filter_spec = ${data.filter}
        RETURNING *
      """.executeInsert(parser.single)
    }
  }
}
