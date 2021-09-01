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

  private val redirectParser = (str("old_id") ~ str("new_id"))
    .map { case oldId ~ newId => oldId -> newId }

  private val cleanupRedirectParser = (str("from_item_id") ~ str("to_item_id"))
    .map { case oldId ~ newId => oldId -> newId }

  private val statParser =
    Macro.parser[ImportLogSummary]("id", "repo_id", "import_dataset_id", "event_id", "timestamp", "created", "updated", "unchanged")


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

  override def list(repoId: String, dsId: Option[String] = None): Future[Seq[ImportLogSummary]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT
              log.id,
              log.repo_id,
              log.import_dataset_id,
              log.event_id,
              log.created AS timestamp,
              sum(case when map.type = 'created' then 1 else 0 end) AS created,
              sum(case when map.type = 'updated' then 1 else 0 end) AS updated,
              sum(case when map.type = 'unchanged' then 1 else 0 end) AS unchanged
            FROM
              import_log log,
              import_file_mapping map
            WHERE
              log.repo_id = $repoId
              AND ($dsId IS NULL OR log.import_dataset_id = $dsId)
              AND log.id = map.import_log_id
            GROUP BY
              log.id, log.repo_id, log.import_dataset_id, timestamp
            ORDER BY
              timestamp DESC""".as(statParser.*)
    }
  }

  override def cleanup(repoId: String, snapshotId: Int): Future[Cleanup] = Future {
    db.withTransaction { implicit conn =>
      // Create a temporary table containing the touched items (file mappings
      // imported since the specified snapshot was taken) and another
      // containing the untouched items (items in the snapshot that are not
      // in the touched set.) Then select the untouched ID
      // and the first touched item that has an ID whose last component
      // matches the local identifier of an untouched item.
      // NOTE: using the local ID component in the ID is a bit of a hack
      // but it can't be helped since we don't have the local ID available
      // in the import log info.

      val ts = System.currentTimeMillis().toString

      SQL"""CREATE TEMP TABLE touched_items_#$ts(
              new_id VARCHAR(8000) PRIMARY KEY,
              suffix TEXT NOT NULL
            )""".executeUpdate()

      SQL"""CREATE INDEX touched_items_#${ts}_suffix ON touched_items_#$ts(suffix)""".executeUpdate()

      SQL"""INSERT INTO touched_items_#$ts
            SELECT DISTINCT map.item_id AS new_id, reverse(split_part(reverse(map.item_id), '-', 1)) as suffix
            FROM import_log log, import_file_mapping map, repo_snapshot snap
            WHERE map.import_log_id = log.id
              AND log.repo_id = $repoId
              AND snap.id = $snapshotId
              AND log.created > snap.created""".executeUpdate()

      SQL"""CREATE TEMP TABLE untouched_items_#$ts(
              id SERIAL PRIMARY KEY,
              old_id TEXT NOT NULL,
              old_local TEXT NOT NULL
            )""".executeUpdate()

      SQL"""CREATE INDEX untouched_items_#${ts}_old_id ON untouched_items_#$ts(old_id)""".executeUpdate()
      SQL"""CREATE INDEX untouched_items_#${ts}_old_local ON untouched_items_#$ts(old_local)""".executeUpdate()

      SQL"""INSERT INTO untouched_items_#$ts (old_id, old_local)
            SELECT DISTINCT item.item_id AS old_id, item.local_id AS old_local
            FROM repo_snapshot_item item
              JOIN repo_snapshot snap ON item.repo_snapshot_id = snap.id
            WHERE snap.id = $snapshotId
              AND NOT EXISTS (
                SELECT DISTINCT new_id FROM touched_items_#$ts
                  WHERE item.item_id = new_id
              )""".executeUpdate()

      SQL"""CREATE TEMP TABLE redirecting_#$ts(
               id SERIAL PRIMARY KEY,
               old_id TEXT NOT NULL,
               new_id TEXT NOT NULL
            )""".executeUpdate()

      SQL"""CREATE INDEX redirecting_#${ts}_old_id ON redirecting_#$ts(old_id)""".executeUpdate()

      SQL"""INSERT INTO redirecting_#$ts (old_id, new_id)
            SELECT DISTINCT ON (new_id) old_id, new_id
            FROM untouched_items_#$ts, touched_items_#$ts
              WHERE suffix = old_local""".executeUpdate()

      val redirects = SQL"SELECT * FROM redirecting_#$ts ORDER BY old_id".as(redirectParser.*)
      val deletions =
        SQL"""SELECT DISTINCT ut.old_id
              FROM untouched_items_#$ts ut
              ORDER BY ut.old_id DESC""".as(str("old_id").*)

      Cleanup(redirects, deletions)
    }
  }

  override def saveCleanup(repoId: String, snapshotId: Int, cleanup: Cleanup): Future[Int] = Future {
    db.withTransaction { implicit conn =>
      val id: Int = SQL"""INSERT INTO cleanup_action (repo_snapshot_id)
          VALUES ($snapshotId)
          RETURNING id""".as(scalar[Int].single)

      if (cleanup.deletions.nonEmpty) {
        val q = """INSERT INTO cleanup_action_deletion (cleanup_action_id, item_id)
                   VALUES ({cleanup_action_id}, {item_id})"""
        val batch = cleanup.deletions.map { itemId =>
          Seq[NamedParameter](
            "cleanup_action_id" -> id,
            "item_id" -> itemId
          )
        }

        BatchSql(q, batch.head, batch.tail: _*).execute()
      }

      if (cleanup.redirects.nonEmpty) {
        val q = """INSERT INTO cleanup_action_redirect (cleanup_action_id, from_item_id, to_item_id)
                   VALUES ({cleanup_action_id}, {from_item_id}, {to_item_id})"""
        val batch = cleanup.redirects.map { case (fromItem, toItem) =>
          Seq[NamedParameter](
            "cleanup_action_id" -> id,
            "from_item_id" -> fromItem,
            "to_item_id" -> toItem
          )
        }

        BatchSql(q, batch.head, batch.tail: _*).execute()
      }
      id
    }
  }

  override def getCleanup(id: String, snapshotId: Int, cleanupId: Int): Future[Cleanup] = Future {
    db.withConnection { implicit conn =>
      val deletions =
        SQL"""SELECT item_id
              FROM cleanup_action_deletion
              WHERE cleanup_action_id = $id""".as(scalar[String].*)
      val redirects =
        SQL"""SELECT from_item_id, to_item_id
              FROM cleanup_action_redirect
              WHERE cleanup_action_id = $id""".as(cleanupRedirectParser.*)
      Cleanup(redirects, deletions)
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
            ORDER BY item.item_id""".as(idMapParser.*)
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
