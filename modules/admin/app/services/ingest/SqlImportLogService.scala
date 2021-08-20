package services.ingest

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import anorm.SqlParser._
import anorm._
import models.ImportLog
import play.api.Logger
import play.api.db.Database
import services.ingest.IngestService.IngestData

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


case class SqlImportLogService @Inject()(db: Database, actorSystem: ActorSystem) extends ImportLogService {
  private val logger: Logger = Logger(classOf[SqlImportLogService])

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.db-writes")

  private implicit val mat: Materializer = Materializer(actorSystem)

  private implicit val parser: RowParser[ImportFileHandle] =
    Macro.parser[ImportFileHandle]("event_id", "repo_id", "import_dataset_id", "key", "version_id")

  private val snapshotParser: RowParser[Snapshot] =
    Macro.parser[Snapshot]("id", "created", "notes")

  private val idMapParser = (str("item_id") ~ str("local_id"))
    .map { case item ~ local => item -> local }



  override def getHandles(unitId: String): Future[Seq[ImportFileHandle]] = Future {
    db.withConnection { implicit conn =>
      // NB: it doesn't matter if we select an event with type 'unchanged' here
      // because the file will still reflect the most up-to-date info we have
      // on the object...
      SQL"""SELECT log.event_id, log.repo_id, log.import_dataset_id, map.key, map.version_id
          FROM import_log log, import_file_mapping map
          WHERE log.id = map.import_log_id
            AND log.event_id IS NOT NULL
            AND map.item_id = $unitId
          ORDER BY log.created DESC""".as(parser.*)
    }
  }

  override def updateHandles(mappings: Seq[(String, String)]): Future[Int] = Future {
    if (mappings.isEmpty) 0 else db.withTransaction { implicit conn =>
      mappings.foldLeft(0) { case (acc, (from, to)) =>
        acc + SQL"""UPDATE import_file_mapping
            SET item_id = $to
            WHERE item_id = $from""".executeUpdate()
      }
    }
  }

  override def save(repoId: String, datasetId: String, job: IngestData, log: ImportLog): Future[Unit] = Future {
    db.withTransaction { implicit conn =>

      val logId: Int = SQL"""INSERT INTO import_log (event_id, repo_id, import_dataset_id)
                                VALUES (${log.event}, $repoId, $datasetId)
                                RETURNING id""".as(scalar[Int].single)

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

      if (log.unchangedKeys.nonEmpty) {
        val params = inserts(log.unchangedKeys, ImportLogOpType.Unchanged)
        BatchSql(q, params.head, params.tail: _*).execute()
      }

      if (log.errors.nonEmpty) {
        val q =
          """INSERT INTO import_error (import_log_id, key, version_id, error_text)
             VALUES ({import_log_id}, {key}, {version_id}, {error_text})"""
        val inserts = (for {
          (path, errorText) <- log.errors
          (key, versionId) = parseKey(path)
        } yield Seq[NamedParameter](
          "import_log_id" -> logId,
          "key" -> key,
          "version_id" -> versionId,
          "error_text" -> errorText
        )).toSeq

        BatchSql(q, inserts.head, inserts.tail: _*).execute()
      }
    }
  }

  override def snapshots(repoId: String): Future[Seq[Snapshot]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT id, created, notes FROM repo_snapshot WHERE repo_id = $repoId ORDER BY created DESC".as(snapshotParser.*)
    }
  }

  override def snapshotIdMap(id: Int): Future[Seq[(String, String)]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT item_id, local_id
            FROM repo_snapshot_item
            WHERE repo_snapshot_id = $id""".as(idMapParser.*)
    }
  }

  override def saveSnapshot(repoId: String, idMap: Source[(String, String), _], notes: Option[String] = None): Future[Snapshot] = {
    idMap.grouped(2000).runWith(Sink.seq).map { batches =>
      db.withTransaction { implicit conn =>
        val snapshot: Snapshot =
          SQL"""INSERT INTO repo_snapshot (repo_id, notes)
                        VALUES ($repoId, $notes) RETURNING id, created, notes""".as(snapshotParser.single)

        batches.foreach { idMap =>
          if (idMap.nonEmpty) {
            logger.debug(s"Inserting batch of ${idMap.size} snapshot items...")
            val inserts = for ((itemId, localId) <- idMap) yield Seq[NamedParameter](
              "repo_snapshot_id" -> snapshot.id,
              "item_id" -> itemId,
              "local_id" -> localId
            )

            val q =
              """INSERT INTO repo_snapshot_item (repo_snapshot_id, item_id, local_id)
                 VALUES ({repo_snapshot_id}, {item_id}, {local_id})"""

            BatchSql(q, inserts.head, inserts.tail: _*).execute()
          }
        }
        snapshot
      }
    }
  }

  override def findUntouchedItemIds(repoId: String, snapshotId: Int): Future[Seq[(String, String)]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT DISTINCT item.item_id, item.local_id
            FROM repo_snapshot_item item
              JOIN repo_snapshot snap ON item.repo_snapshot_id = snap.id
            WHERE snap.id = $snapshotId
              AND NOT EXISTS (
                SELECT DISTINCT map.item_id FROM import_file_mapping map
                  JOIN import_log log ON map.import_log_id = log.id
                  WHERE log.repo_id = $repoId
                    AND map.item_id = item.item_id
                    AND log.created > snap.created
              )
            ORDER BY item.item_id
            """.as(idMapParser.*)
    }
  }

  override def errors(repoId: String, datasetId: String): Future[Seq[(String, String)]] = Future {
    db.withConnection { implicit conn =>
      val pairParser = (str("key") ~ str("error_text"))
        .map { case key ~ err => key -> err}

      val latestId: Option[Int] =
        SQL"""SELECT id
              FROM import_log
              WHERE repo_id = $repoId
                AND import_dataset_id = $datasetId
                ORDER BY created DESC
                LIMIT 1""".as(scalar[Int].singleOpt)

      latestId.fold(ifEmpty = Seq.empty[(String, String)]) { (id: Int) =>
        SQL"""SELECT DISTINCT key, error_text
            FROM import_error
            WHERE import_log_id = $id""".as(pairParser.*)
      }
    }
  }

  override def deleteSnapshot(repoId: String, id: Int): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL"""DELETE FROM repo_snapshot
            WHERE id = $id
              AND repo_id = $repoId""".executeUpdate()
    }
  }
}
