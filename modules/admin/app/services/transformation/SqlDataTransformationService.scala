package services.transformation

import _root_.utils.{db => dbUtils}
import org.apache.pekko.actor.ActorSystem
import anorm.postgresql._
import anorm.{Macro, RowParser, _}
import models.{DataTransformation, DataTransformationInfo}
import play.api.Logger
import play.api.db.Database
import play.api.libs.json.JsObject

import java.sql.SQLException
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class SqlDataTransformationService @Inject()(db: Database, actorSystem: ActorSystem) extends DataTransformationService {

  private val logger: Logger = Logger(classOf[SqlDataTransformationService])

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[DataTransformation] =
    Macro.parser[DataTransformation](
      "id", "name", "repo_id", "type", "map", "created", "comments", "has_params")

  override def list(): Future[Seq[DataTransformation]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM data_transformation ORDER BY id".as(parser.*)
    }
  }

  override def get(id: String): Future[DataTransformation] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM data_transformation WHERE id = $id".as(parser.single)
    }
  }

  override def get(ids: Seq[String]): Future[Seq[DataTransformation]] = Future {
    if (ids.isEmpty) Seq.empty else db.withConnection { implicit conn =>
      val dts = SQL"SELECT * FROM data_transformation WHERE id IN ($ids)".as(parser.*)
      ids.map(id => dts.find(_.id == id)
        .getOrElse(throw new Exception(s"Transformation cannot be found: '$id'")))
    }
  }

  override def delete(id: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM data_transformation WHERE id = $id".executeUpdate() == 1
    }
  }

  override def create(info: DataTransformationInfo, repoId: Option[String]): Future[DataTransformation] = Future {
    db.withTransaction { implicit conn =>
      try {
        val strId = dbUtils.newObjectId(10)
        SQL"""INSERT INTO data_transformation
                (id, repo_id, name, type, map, comments, has_params)
              VALUES (
                $strId,
                $repoId,
                ${info.name},
                ${info.bodyType},
                ${info.body},
                ${info.comments},
                ${info.hasParams}
              )
              RETURNING *
            """.as(parser.single)
      } catch {
        case e: SQLException if e.getSQLState == "23505" => // unique violation
          throw DataTransformationExists(info.name, e)
      }
    }
  }

  override def update(id: String, info: DataTransformationInfo, repoId: Option[String]): Future[DataTransformation] = Future {
    db.withConnection { implicit conn =>
      try {
        SQL"""UPDATE data_transformation SET
              repo_id = $repoId,
              name = ${info.name},
              type = ${info.bodyType},
              map = ${info.body},
              comments = ${info.comments},
              has_params = ${info.hasParams}
            WHERE id = $id
            RETURNING *
          """.as(parser.single)
      } catch {
        case e: SQLException if e.getSQLState == "23505" => // unique violation
          throw DataTransformationExists(info.name, e)
      }
    }
  }

  override def getConfig(repoId: String, datasetId: String): Future[Seq[(String, JsObject)]] = Future {
    db.withConnection { implicit conn =>
      val tupleParser = (SqlParser.str("id") ~ SqlParser.get[JsObject]("parameters"))
        .map { case id ~ json => id -> json}

      SQL"""SELECT dt.id, tc.parameters FROM data_transformation dt
           LEFT JOIN transformation_config tc ON tc.data_transformation_id = dt.id
           WHERE tc.repo_id = $repoId
             AND tc.import_dataset_id = $datasetId
           ORDER BY tc.ordering ASC""".as(tupleParser.*)
    }
  }

  override def saveConfig(repoId: String, datasetId: String, dtIds: Seq[(String, JsObject)]): Future[Int] = Future {
    db.withTransaction { implicit conn =>
      // First, delete all the existing mappings:
      SQL"""DELETE FROM transformation_config
            WHERE repo_id = $repoId
              AND import_dataset_id = $datasetId""".execute()

      if (dtIds.isEmpty) 0 else {
        val q =
          """INSERT INTO transformation_config
                   VALUES({repo_id}, {import_dataset_id}, {ordering}, {data_transformation_id}, {parameters})
                   ON CONFLICT (repo_id, import_dataset_id, ordering)
                   DO UPDATE SET data_transformation_id = {data_transformation_id}, parameters = {parameters}"""

        val inserts = dtIds.zipWithIndex.map { case ((dtId, params), i) =>
          Seq[NamedParameter](
            "repo_id" -> repoId,
            "import_dataset_id" -> datasetId,
            "ordering" -> i,
            "data_transformation_id" -> dtId,
            "parameters" -> params,
            "data_transformation_id" -> dtId,
            "parameters" -> params
          )
        }
        val batch = BatchSql(q, inserts.head, inserts.tail: _*)
        val rows: Array[Int] = batch.execute()
        rows.count(_ > 0)
      }
    }
  }
}
