package services.ingest

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
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
    createdKeys = Map(keyVersion -> Seq("unit-1", "unit-2")),
    event = Some(eventId)
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

    "handle snapshots" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      val idMap = List("r1-1" -> "1", "r1-2" -> "2")
      val s = await(service.saveSnapshot("r1", Source(idMap), Some("Testing...")))
      val list = await(service.snapshots("r1"))
      list.size must_== 1
      list.head must_== s

      await(service.snapshotIdMap(s.id)) must_== idMap
    }

    "create item diffs" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      success
    }
  }
}
