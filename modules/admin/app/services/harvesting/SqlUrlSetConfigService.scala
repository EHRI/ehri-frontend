package services.harvesting

import org.apache.pekko.actor.ActorSystem
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

  private implicit val parser: RowParser[UrlSetConfig] = {
    SqlParser.get("url_map")(fromJson[Seq[(String, String)]]) ~
      SqlParser.str("method") ~
    SqlParser.get("headers")(fromJson[Seq[(String, String)]]).?
  }.map { case um ~ m ~ h => UrlSetConfig(um, method = m, headers = h)}

  override def get(id: String, ds: String): Future[Option[UrlSetConfig]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT url_map, method, headers
           FROM import_url_set_config
           WHERE repo_id = $id
            AND import_dataset_id = $ds"""
        .as(parser.singleOpt)
    }
  }

  override def delete(id: String, ds: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""DELETE FROM import_url_set_config
           WHERE repo_id = $id
             AND import_dataset_id = $ds""".executeUpdate() == 1
    }
  }

  override def save(id: String, ds: String, data: UrlSetConfig): Future[UrlSetConfig] = Future {
    db.withTransaction { implicit conn =>
      SQL"""INSERT INTO import_url_set_config
        (repo_id, import_dataset_id, url_map, method, headers)
        VALUES (
          $id,
          $ds,
          ${asJson(data.urlMap)},
          ${data.method},
          ${data.headers.map(kv => Json.toJson(kv))}
      ) ON CONFLICT (repo_id, import_dataset_id) DO UPDATE
        SET
          url_map = ${asJson(data.urlMap)},
          method = ${data.method},
          headers = ${data.headers.map(kv => Json.toJson(kv))}
        RETURNING url_map, method, headers
      """.executeInsert(parser.single)
    }
  }
}
