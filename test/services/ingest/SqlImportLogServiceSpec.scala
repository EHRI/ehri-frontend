package services.ingest

import java.util.UUID

import akka.actor.ActorSystem
import defines.ContentTypes
import helpers._
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification
import services.data.AnonymousUser
import services.ingest.IngestService.IngestData

class SqlImportLogServiceSpec extends PlaySpecification {

  private val actorSystem = new GuiceApplicationBuilder().build().injector.instanceOf[ActorSystem]

  def service(implicit db: Database) = SqlImportLogService(db, actorSystem)

  "import log service should" should {
    "save items" in withDatabaseFixture("data-transformation-fixtures.sql") { implicit db =>
      val keyVersion = "foo.ead?versionId=1"
      val job: IngestData = IngestData(
        IngestParams(
          ContentTypes.Repository,
          scope = "r1",
          log = "Testing...",
          data = UrlMapPayload(Map(keyVersion -> java.net.URI.create("http://example.com/foo.ead")))
        ),
        IngestService.IngestDataType.Ead,
        "application/json",
        AnonymousUser,
      )
      val log: ImportLog = ImportLog(
        createdKeys = Map(keyVersion -> Seq("unit-1", "unit-2")),
        event = Some(UUID.randomUUID().toString)
      )

      await(service.save("r1", "default", job, log))

      val handle = await(service.getHandle("unit-1"))
      handle must beSome(ImportFileHandle("r1", "default", "foo.ead", Some("1")))
    }
  }
}
