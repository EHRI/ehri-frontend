package services.ingest

import java.util.UUID
import akka.actor.ActorSystem
import helpers._
import models.ContentTypes
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification
import services.data.AnonymousUser
import services.ingest.IngestService.IngestData

class SqlImportLogServiceSpec extends PlaySpecification {

  private val actorSystem = new GuiceApplicationBuilder().build().injector.instanceOf[ActorSystem]

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
  }
}
