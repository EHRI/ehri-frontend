package services.ingest

import akka.actor.ActorSystem
import anorm.SqlParser.scalar
import anorm._
import javax.inject.Inject
import play.api.Logger
import play.api.db.Database
import services.ingest.IngestApi.IngestData

import scala.concurrent.{ExecutionContext, Future}
case class SqlImportLogService @Inject()(db: Database, actorSystem: ActorSystem) extends ImportLogService {
  private val logger: Logger = Logger(classOf[SqlImportLogService])

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val parser: RowParser[ImportFileHandle] =
    Macro.parser[ImportFileHandle]("repo_id", "import_dataset_id", "key", "version_id")

  override def getHandle(unitId: String): Future[Option[ImportFileHandle]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT l.repo_id, l.import_dataset_id, m.key, m.version_id
          FROM import_log l, import_file_mapping m
          WHERE l.id = m.import_log_id AND m.item_id = $unitId
          ORDER BY l.created DESC LIMIT 1""".as(parser.singleOpt)
    }
  }

  override def save(repoId: String, datasetId: String, job: IngestData, log: ImportLog): Future[Unit] = Future {
    db.withTransaction { implicit conn =>

      val logId: String = SQL"""INSERT INTO import_log (id, repo_id, import_dataset_id)
                                VALUES (${log.event}, $repoId, $datasetId)
                                RETURNING id""".as(scalar[String].single)

      def parseKey(key: String): (String, Option[String]) = {
        val parts = key.split("\\?versionId=")
        (parts.head, parts.drop(1).headOption)
      }

      def inserts(mappings: Map[String, Seq[String]], t: ImportLogOpType.Value): Seq[Seq[NamedParameter]] =
        (for {
          (path, created) <- mappings
          (key, versionId) = parseKey(path)
          unitId <- created
        } yield Seq[NamedParameter](
          "import_log_id" -> logId,
          "key" -> key,
          "version_id" -> versionId,
          "item_id" -> unitId,
          "type" -> t
        )).toSeq

      val q = """INSERT INTO import_file_mapping (import_log_id, key, version_id, item_id, type)
                  VALUES ({import_log_id}, {key}, {version_id}, {item_id}, {type})"""

      if (log.createdKeys.nonEmpty) {
        val params = inserts(log.createdKeys, ImportLogOpType.Created)
        BatchSql(q, params.head, params.tail: _*).execute()
      }

      if (log.updatedKeys.nonEmpty) {
        val params = inserts(log.updatedKeys, ImportLogOpType.Updated)
        BatchSql(q, params.head, params.tail: _*).execute()
      }
    }
  }
}
