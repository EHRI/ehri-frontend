package services.datasets

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
      "repo_id", "id", "name", "type", "created", "comments")

  override def get(repoId: String, datasetId: String): Future[ImportDataset] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT * FROM import_dataset WHERE repo_id = $repoId AND id = $datasetId""".as(parser.single)
    }
  }(ec)

  override def list(repoId: String): Future[Seq[ImportDataset]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT * FROM import_dataset WHERE repo_id = $repoId""".as(parser.*)
    }
  }(ec)

  override def delete(repoId: String, datasetId: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""DELETE FROM import_dataset WHERE repo_id = $repoId AND id = $datasetId""".executeUpdate() == 1
    }
  }(ec)

  override def create(repoId: String, info: ImportDatasetInfo): Future[ImportDataset] = Future {
    db.withConnection { implicit conn =>
      SQL"""INSERT INTO import_dataset (repo_id, id, name, type, comments)
          VALUES (
            $repoId,
            ${info.id},
            ${info.name},
            ${info.src},
            ${info.notes}
          )
          RETURNING *""".as(parser.single)
    }
  }(ec)
}
