package integration.admin

import helpers._
import models.ImportLog
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.ingest.Coreference


class CoreferenceTablesSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser
  private val routes = controllers.datasets.routes.CoreferenceTables

  "Coreference API" should {

    "get tables" in new DBTestApp("coreference-fixtures.sql") {
      val r = FakeRequest(routes.getTable("r1"))
        .withUser(privilegedUser)
        .call()
      contentAsJson(r) must_== Json.toJson(Seq(
        Coreference("Person 1", "a1", "auths"),
        Coreference("Person 2", "a2", "auths")
      ))
    }

    "save tables" in new DBTestApp("coreference-fixtures.sql") {
      val r = FakeRequest(routes.saveTable("r1"))
        .withUser(privilegedUser)
        .call()
      contentAsJson(r) must_== Json.obj("ok" -> true)
    }

    "ingest tables" in new DBTestApp("coreference-fixtures.sql") {
      val r = FakeRequest(routes.ingestTable("r1"))
        .withUser(privilegedUser)
        .call()
      // Nothing is updated here, hence an empty log...
      contentAsJson(r) must_== Json.toJson(ImportLog())
    }
  }
}
