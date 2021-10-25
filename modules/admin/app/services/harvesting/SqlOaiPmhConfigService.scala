package services.harvesting

import akka.actor.ActorSystem
import anorm.{RowParser, SqlParser, _}
import models.OaiPmhConfig
import play.api.db.Database

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlOaiPmhConfigService @Inject()(db: Database, actorSystem: ActorSystem) extends OaiPmhConfigService {

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  // NB: this row parser deliberately does not populate the `auth` parameter
  private implicit val parser: RowParser[OaiPmhConfig] = {
    SqlParser.str("endpoint_url") ~
      SqlParser.str("metadata_prefix") ~
      SqlParser.get[Option[String]]("set_spec")
    }.map {
      case url ~ prefix ~ spec => OaiPmhConfig(url, prefix, spec)
    }

  override def get(id: String, ds: String): Future[Option[OaiPmhConfig]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM oaipmh_config WHERE repo_id = $id AND import_dataset_id = $ds"
        .as(parser.singleOpt)
    }
  }

  override def delete(id: String, ds: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM oaipmh_config WHERE repo_id = $id AND import_dataset_id = $ds".executeUpdate() == 1
    }
  }

  override def save(id: String, ds: String, data: OaiPmhConfig): Future[OaiPmhConfig] = Future {
    db.withTransaction { implicit conn =>
      SQL"""INSERT INTO oaipmh_config
        (repo_id, import_dataset_id, endpoint_url, metadata_prefix, set_spec)
        VALUES (
          $id,
          $ds,
          ${data.url},
          ${data.format},
          ${data.set}
      ) ON CONFLICT (repo_id, import_dataset_id) DO UPDATE
        SET
          endpoint_url = ${data.url},
          metadata_prefix = ${data.format},
          set_spec = ${data.set}
        RETURNING *
      """.executeInsert(parser.single)
    }
  }
}
