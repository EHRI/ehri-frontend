package integration.admin

import helpers._
import mockdata.privilegedUser
import models.ImportConfig
import play.api.libs.json.{JsNull, Json}
import play.api.test.FakeRequest


class ImportConfigsSpec extends IntegrationTestRunner with ResourceUtils {

  private val importConfigRoutes = controllers.datasets.routes.ImportConfigs


  "Import Configs API" should {

    "get configs with no data" in new ITestApp {
      val r = FakeRequest(importConfigRoutes.get("r1", "default"))
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r) must_== JsNull
    }

    "get configs" in new DBTestApp("import-config-fixtures.sql") {
      val r = FakeRequest(importConfigRoutes.get("r1", "default"))
        .withUser(privilegedUser)
        .call()
      status(r) must_== OK
      contentAsJson(r).asOpt[ImportConfig] must beSome.which { c: ImportConfig =>
        c.properties must beSome("r1-ead.properties")
      }
    }

    "save configs" in new DBTestApp("import-config-fixtures.sql") {
      val data = Json.obj(
        "allowUpdates" -> true,
        "useSourceId" -> false,
        "tolerant" -> false,
        "properties" -> None,
        "defaultLang" -> None,
        "logMessage" -> "test",
        "batchSize" -> None,
        "comments" -> Some("Testing testing... 1, 2, 3...")
      )
      val r = FakeRequest(importConfigRoutes.save("r1", "default"))
        .withUser(privilegedUser)
        .callWith(data)
      status(r) must_== OK
      contentAsJson(r).asOpt[ImportConfig] must beSome.which { c: ImportConfig =>
        c.comments must beSome("Testing testing... 1, 2, 3...")
      }
    }

    "ingest files" in new DBTestApp("import-config-fixtures.sql") {
      // NB: this job will fail (or do nothing) because there are no files
      // in the test dataset
      val data = Json.obj(
        "allowUpdates" -> true,
        "useSourceId" -> false,
        "tolerant" -> false,
        "properties" -> None,
        "defaultLang" -> None,
        "logMessage" -> "test",
        "batchSize" -> None,
        "comments" -> Some("Testing testing... 1, 2, 3...")
      )
      val payload = Json.obj(
        "config" -> data,
        "commit" -> true,
        "files" -> Seq.empty[String]
      )
      val r = FakeRequest(importConfigRoutes.ingestFiles("r1", "default"))
        .withUser(privilegedUser)
        .callWith(payload)
      status(r) must_== OK
    }
  }
}
