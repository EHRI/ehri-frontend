package services.ingest

import akka.actor.ActorSystem
import anorm.{Macro, RowParser, _}
import models.ImportConfig
import play.api.db.Database

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlImportConfigService @Inject()(db: Database, actorSystem: ActorSystem) extends ImportConfigService {

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[ImportConfig] =
    Macro.parser[ImportConfig](
      "allow_updates", "use_source_id", "tolerant", "properties_file", "default_lang", "log_message", "comments")

  override def get(id: String, ds: String): Future[Option[ImportConfig]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM import_config WHERE repo_id = $id AND import_dataset_id = $ds"
        .as(parser.singleOpt)
    }
  }

  override def delete(id: String, ds: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM import_config WHERE repo_id = $id AND import_dataset_id = $ds".executeUpdate() == 1
    }
  }

  override def save(id: String, ds: String, data: ImportConfig): Future[ImportConfig] = Future {
    db.withTransaction { implicit conn =>
      SQL"""INSERT INTO import_config
        (repo_id, import_dataset_id, allow_updates, use_source_id, tolerant, properties_file, default_lang, log_message, comments)
        VALUES (
          $id,
          $ds,
          ${data.allowUpdates},
          ${data.useSourceId},
          ${data.tolerant},
          ${data.properties},
          ${data.defaultLang},
          ${data.logMessage},
          ${data.comments}
      ) ON CONFLICT (repo_id, import_dataset_id) DO UPDATE
        SET
          allow_updates = ${data.allowUpdates},
          use_source_id = ${data.useSourceId},
          tolerant = ${data.tolerant},
          properties_file = ${data.properties},
          default_lang = ${data.defaultLang},
          log_message = ${data.logMessage},
          comments = ${data.comments}
        RETURNING *
      """.executeInsert(parser.single)
    }
  }
}
