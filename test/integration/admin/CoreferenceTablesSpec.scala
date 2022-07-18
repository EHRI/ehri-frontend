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

    "import tables" in new DBTestApp("coreference-fixtures.sql") {
      val r = FakeRequest(routes.importTable("r1"))
        .withUser(privilegedUser)
        .callWith(Json.toJson(Seq(
          Coreference("Person 1", "a2", "auths")
        )))
      contentAsJson(r) must_== Json.obj("imported" -> 1)
    }

    "extract tables" in new DBTestApp("coreference-fixtures.sql") {
      // FIXME: current Neo4j fixtures don't exercise this test!
      val r = FakeRequest(routes.extractTable("r1"))
        .withUser(privilegedUser)
        .call()
      contentAsJson(r) must_== Json.obj("imported" -> 0)
    }

    "ingest tables" in new DBTestApp("coreference-fixtures.sql") {
      val r = FakeRequest(routes.applyTable("r1"))
        .withUser(privilegedUser)
        .call()
      // Nothing is updated here, hence an empty log...
      contentAsJson(r) must_== Json.toJson(ImportLog())
    }

    "delete table values" in new DBTestApp("coreference-fixtures.sql") {
      val r = FakeRequest(routes.deleteTable("r1"))
        .withUser(privilegedUser)
        .callWith(Json.toJson(Seq(
          Coreference("Person 1", "a1", "auths"),
          Coreference("Person 2", "a2", "auths")
        )))
      contentAsJson(r) must_== Json.obj("deleted"-> 2)
    }
  }
}
