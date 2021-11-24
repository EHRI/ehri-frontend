package services.harvesting

import akka.actor.ActorSystem
import anorm.postgresql._
import anorm.{RowParser, _}
import models.UrlSetConfig
import play.api.db.Database
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlUrlSetConfigService @Inject()(db: Database, actorSystem: ActorSystem) extends UrlSetConfigService {

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[UrlSetConfig] = SqlParser.scalar(fromJson[Seq[(String, String)]])
    .map(m => UrlSetConfig(m))

  override def get(id: String, ds: String): Future[Option[UrlSetConfig]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT url_map FROM import_url_set_config WHERE repo_id = $id AND import_dataset_id = $ds"
        .as(parser.singleOpt)
    }
  }

  override def delete(id: String, ds: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM import_url_set_config WHERE repo_id = $id AND import_dataset_id = $ds".executeUpdate() == 1
    }
  }

  override def save(id: String, ds: String, data: UrlSetConfig): Future[UrlSetConfig] = Future {
    db.withTransaction { implicit conn =>
      SQL"""INSERT INTO import_url_set_config
        (repo_id, import_dataset_id, url_map)
        VALUES (
          $id,
          $ds,
          ${Json.toJson(data.urlMap)}
      ) ON CONFLICT (repo_id, import_dataset_id) DO UPDATE
        SET
          url_map = ${Json.toJson(data.urlMap)}
        RETURNING url_map
      """.executeInsert(parser.single)
    }
  }
}
