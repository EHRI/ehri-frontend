package services.transformation

import java.sql.SQLException

import akka.actor.ActorSystem
import anorm.{Macro, RowParser, SqlParser, _}
import javax.inject.{Inject, Singleton}
import models.{DataTransformation, DataTransformationInfo}
import play.api.Logger
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlDataTransformationService @Inject()(db: Database, actorSystem: ActorSystem) extends DataTransformationService {

  private val logger: Logger = Logger(classOf[SqlDataTransformationService])

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[DataTransformation] =
    Macro.parser[DataTransformation](
      "id", "name", "repo_id", "type", "map", "created", "comments")

  override def list(): Future[Seq[DataTransformation]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM data_transformation ORDER BY id".as(parser.*)
    }
  }

  override def get(id: Long): Future[DataTransformation] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM data_transformation WHERE id = $id"
        .as(parser.single)
    }
  }

  override def get(ids: Seq[Long]): Future[Seq[DataTransformation]] = Future {
    if (ids.isEmpty) Seq.empty else db.withConnection { implicit conn =>
      val dts = SQL"SELECT * FROM data_transformation WHERE id IN ($ids)".as(parser.*)
      ids.flatMap(id => dts.find(_.id == id).toSeq)
    }
  }

  override def delete(id: Long): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM data_transformation WHERE id = $id".executeUpdate() == 1
    }
  }

  override def create(info: DataTransformationInfo, repoId: Option[String]): Future[DataTransformation] = Future {
    db.withTransaction { implicit conn =>
      try {
        val id = SQL"""INSERT INTO data_transformation
        (repo_id, name, type, map, comments)
        VALUES (
          $repoId,
          ${info.name},
          ${info.bodyType},
          ${info.body},
          ${info.comments}
      )
      """.executeInsert(SqlParser.scalar[Int].single)

        // FIXME: not using PostgreSQL 'returning' stmt for testing h2 compat
        SQL"SELECT * FROM data_transformation WHERE id = $id".as(parser.single)
      } catch {
        case e: SQLException if e.getSQLState == "23505" => // unique violation
          throw DataTransformationExists(info.name, e)
      }
    }
  }

  override def update(id: Long, info: DataTransformationInfo, repoId: Option[String]): Future[DataTransformation] = Future {
    db.withTransaction { implicit conn =>
      try {
        SQL"""UPDATE data_transformation SET
              repo_id = $repoId,
              name = ${info.name},
              type = ${info.bodyType},
              map = ${info.body},
              comments = ${info.comments}
            WHERE id = $id
          """.executeUpdate()
      // FIXME: not using PostgreSQL 'returning' stmt for testing h2 compat
      SQL"SELECT * FROM data_transformation WHERE id = $id".as(parser.single)
    } catch {
        case e: SQLException if e.getSQLState == "23505" => // unique violation
          throw DataTransformationExists(info.name, e)
      }
    }
  }

  override def check(info: DataTransformationInfo): Future[Boolean] = {
    ???
  }

  override def getConfig(repoId: String): Future[Seq[DataTransformation]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT dt.* FROM data_transformation dt
           LEFT JOIN transformation_config tc ON tc.data_transformation_id = dt.id
           WHERE tc.repo_id = $repoId ORDER BY tc.ordering ASC""".as(parser.*)
    }
  }

  override def saveConfig(repoId: String, dtIds: Seq[Long]): Future[Int] = Future {
    db.withTransaction { implicit conn =>

      // First, delete all the existing mappings:
      SQL"""DELETE FROM transformation_config WHERE repo_id = $repoId""".execute()

      if (dtIds.isEmpty) 0 else {
        val dbName = conn.getMetaData.getDatabaseProductName.toLowerCase

        val qs =
          """INSERT INTO transformation_config
          VALUES({repo_id}, {ordering}, {data_transformation_id})"""

        // For reasons I don't understand, PostgreSQL will still throw *occasional*
        // duplicate primary key errors even though we should have deleted everything
        // for this item above. For these cases, update the row instead.
        val q = qs + (dbName match {
          case "postgresql" => """
              ON CONFLICT (repo_id, ordering)
              DO UPDATE SET data_transformation_id = {data_transformation_id}"""
          case _ => "" // works fine on H2 as is!
        })
        val inserts = dtIds.zipWithIndex.map { case (dtId, i) =>
          Seq[NamedParameter](
            "repo_id" -> repoId,
            "ordering" -> i,
            "data_transformation_id" -> dtId
          ) ++ (dbName match {
            case "postgresql" => Seq[NamedParameter]("data_transformation_id" -> dtId)
            case _ => Seq.empty[NamedParameter]
          })
        }
        val batch = BatchSql(q, inserts.head, inserts.tail: _*)
        val rows: Array[Int] = batch.execute()
        rows.count(_ > 0)
      }
    }
  }
}
