package services.datasets

import java.sql.SQLException

import akka.actor.ActorSystem
import anorm.{Macro, RowParser}
import javax.inject.Inject
import models.{ImportDataset, ImportDatasetInfo}
import play.api.db.Database
import anorm._

import scala.concurrent.{ExecutionContext, Future}

case class SqlImportDatasetService @Inject()(db: Database, actorSystem: ActorSystem) extends ImportDatasetService {
  private implicit def ec: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[ImportDataset] =
    Macro.parser[ImportDataset](
      "repo_id", "id", "name", "type", "created", "item_id", "sync", "comments")

  override def listAll(): Future[Map[String, Seq[ImportDataset]]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT * FROM import_dataset ORDER BY repo_id, name ASC""".as(parser.*)
        .groupBy(_.repoId)
    }
  }(ec)

  override def get(repoId: String, datasetId: String): Future[ImportDataset] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT * FROM import_dataset WHERE repo_id = $repoId AND id = $datasetId""".as(parser.single)
    }
  }(ec)

  override def list(repoId: String): Future[Seq[ImportDataset]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT * FROM import_dataset
           WHERE repo_id = $repoId
           ORDER BY name ASC""".as(parser.*)
    }
  }(ec)

  override def delete(repoId: String, datasetId: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""DELETE FROM import_dataset WHERE repo_id = $repoId AND id = $datasetId""".executeUpdate() == 1
    }
  }(ec)

  @throws[ImportDatasetExists]
  override def create(repoId: String, info: ImportDatasetInfo): Future[ImportDataset] = Future {
    db.withConnection { implicit conn =>
      try {
        SQL"""INSERT INTO import_dataset (repo_id, id, name, type, item_id, sync, comments)
          VALUES (
            $repoId,
            ${info.id},
            ${info.name},
            ${info.src},
            ${info.fonds.filter(_.trim.nonEmpty)},
            ${info.sync},
            ${info.notes}
          )
          RETURNING *""".as(parser.single)
      } catch {
        case e: SQLException if e.getSQLState == "23505" => // unique violation
          throw ImportDatasetExists(info.id, e)
      }
    }
  }(ec)

  override def update(repoId: String, datasetId: String, info: ImportDatasetInfo): Future[ImportDataset] = Future {
    db.withConnection { implicit conn =>
      SQL"""UPDATE import_dataset
            SET
              name = ${info.name},
              type = ${info.src},
              item_id = ${info.fonds.filter(_.trim.nonEmpty)},
              sync = ${info.sync},
              comments = ${info.notes.filter(_.trim.nonEmpty)}
            WHERE repo_id = $repoId
              AND id = $datasetId
        RETURNING *""".as(parser.single)
    }
  }(ec)
}
