package services.ingest

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import helpers._
import models.{ContentTypes, ImportLog, IngestParams, UrlMapPayload}
import org.specs2.specification.AfterAll
import play.api.db.Database
import play.api.test.PlaySpecification
import services.data.AnonymousUser
import services.ingest.IngestService.IngestData

import java.util.UUID

class SqlImportLogServiceSpec extends PlaySpecification with AfterAll {

  private val actorSystem = ActorSystem()
  override def afterAll(): Unit = await(actorSystem.terminate())

  def service(implicit db: Database) = SqlImportLogService(db, actorSystem)
  private val keyVersion = "foo.ead?versionId=1"
  private val job: IngestData = IngestData(
    IngestParams(
      ContentTypes.Repository,
      scope = "r1",
      log = "Testing...",
      data = UrlMapPayload(Map(keyVersion -> java.net.URI.create("http://example.com/foo.ead")))
    ),
    IngestService.IngestDataType.Ead,
    "application/json",
    AnonymousUser,
    "localhost"
  )
  private val eventId = UUID.randomUUID().toString
  private val log: ImportLog = ImportLog(
    createdKeys = Map(keyVersion -> Seq("unit-2", "unit-3")),
    updatedKeys = Map(keyVersion -> Seq("unit-1")),
    event = Some(eventId),
    errors = Map("bar.ead?version=1" -> "HTTP 503 Error"),
  )

  "import log service should" should {
    "save items and retrieve items" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      await(service.save("r1", "default", job, log))

      val handle = await(service.getHandles("unit-1"))
      handle.headOption must beSome(ImportFileHandle(eventId, "r1", "default", "foo.ead", Some("1")))
    }

    "update handle references" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      await(service.save("r1", "default", job, log))
      await(service.updateHandles(Seq("unit-1" -> "new-unit-1", "unit-2" -> "new-unit-2"))) must_== 2
    }

    "save errors" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      await(service.save("r1", "default", job, log))
      await(service.errors("r1", "default")) must_== log.errors.toSeq
    }

    "handle snapshots" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      val idMap = List("r1-1" -> "1", "r1-2" -> "2")
      val s = await(service.saveSnapshot("r1", Source(idMap), Some("Testing...")))
      val list = await(service.snapshots("r1"))
      list.size must_== 1
      list.head must_== s

      await(service.snapshotIdMap(s.id)) must_== idMap
    }

    "create item diffs" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      // The log contains unit-1 and unit-2 but not unit-3
      val idMap = List("unit-1" -> "1", "unit-2" -> "2", "unit-3" -> "3", "unit-4" -> "4")
      val s = await(service.saveSnapshot("r1", Source(idMap), Some("First...")))
      await(service.save("r1", "default", job, log))

      val diff = await(service.findUntouchedItemIds("r1", s.id))
      diff must_== Seq("unit-4" -> "4")
    }

    "calculate cleanup" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      // The log contains unit-1 and unit-2 but not unit-3
      val idMap = List("oldunit-1" -> "1", "oldunit-2" -> "2", "todelete" -> "del")
      val s = await(service.saveSnapshot("r1", Source(idMap), Some("Test...")))
      await(service.save("r1", "default", job, log))
      val cleanup = await(service.cleanup("r1", s.id))
      cleanup.redirects must_== Seq("oldunit-1" -> "unit-1", "oldunit-2" -> "unit-2")
      cleanup.deletions must_== Seq("todelete", "oldunit-2", "oldunit-1")
    }

    "store cleanup logs" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      // The log contains unit-1 and unit-2 but not unit-3
      val idMap = List("oldunit-1" -> "1", "oldunit-2" -> "2", "todelete" -> "del")
      val s = await(service.saveSnapshot("r1", Source(idMap), Some("Test...")))
      await(service.save("r1", "default", job, log))
      val cleanup = await(service.cleanup("r1", s.id))
      val cid = await(service.saveCleanup("r1", s.id, cleanup))
      val cleanup2 = await(service.getCleanup("r1", s.id, cid))
      cleanup must_== cleanup2
    }

    "calculate stats" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      // The log contains unit-1 and unit-2 but not unit-3
      await(service.save("r1", "default", job, log))
      val stats: Seq[ImportLogSummary] = await(service.list("r1", Some("default")))
      stats.size must_== 1
      stats.head.created must_== 2
      stats.head.updated must_== 1
    }
  }
}
