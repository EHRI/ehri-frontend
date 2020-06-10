package services.harvesting

import akka.actor.ActorSystem
import anorm.{Macro, RowParser, _}
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import models.admin.OaiPmhConfig
import play.api.db.Database
import services.data.ItemNotFound

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlOaiPmhConfigService @Inject()(db: Database, actorSystem: ActorSystem) extends OaiPmhConfigService {

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[OaiPmhConfig] =
    Macro.parser[OaiPmhConfig](
      "endpoint_url", "metadata_prefix", "set_spec")

  override def get(id: String): Future[Option[OaiPmhConfig]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM oaipmh_config WHERE repo_id = $id"
        .as(parser.singleOpt)
    }
  }

  override def delete(id: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM oaipmh_config WHERE id = $id".executeUpdate() == 1
    }
  }

  override def save(id: String, data: OaiPmhConfig): Future[OaiPmhConfig] = Future {
    db.withTransaction { implicit conn =>
      SQL"""INSERT INTO oaipmh_config
        (repo_id, endpoint_url, metadata_prefix, set_spec)
        VALUES (
          $id,
          ${data.url},
          ${data.format},
          ${data.set}
      ) ON CONFLICT (repo_id) DO UPDATE
        SET
          endpoint_url = ${data.url},
          metadata_prefix = ${data.format},
          set_spec = ${data.set}
        RETURNING *
      """.executeInsert(parser.single)
    }
  }
}
