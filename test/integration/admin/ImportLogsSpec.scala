package integration.admin

import controllers.datasets.{CleanupConfirmation, CleanupSummary}
import helpers._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.ingest.Cleanup


class ImportLogsSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser
  private val routes = controllers.datasets.routes.ImportLogs

  "Import Logs API" should {

    "get heuristic cleanup" in new DBTestApp("import-log-fixture.sql") {
      val r = FakeRequest(routes.cleanup("r1", 1))
        .withUser(privilegedUser)
        .call()
      contentAsJson(r) must_== Json.toJson(Cleanup(
        deletions = Seq("nl-r1-m19", "c4"),
        redirects = Seq("nl-r1-m19" -> "nl-r1-TEST-m19")
      ))
    }

    "perform cleanup" in new DBTestApp("import-log-fixture.sql") {
      val expected = CleanupSummary(
        // we delete both the deleted item and the moved item's source
        deletions = 2,
        // this would be 1 except the fixture item nl-r1-TEST-m19
        // doesn't actually exist on the backend
        relinks = 0,
        // 1 redirect for both admin and public pages, so
        // double the number the actual items moved
        redirects = 2
      )
      val r = FakeRequest(routes.doCleanup("r1", 1))
        .withUser(privilegedUser)
        .callWith(Json.toJson(CleanupConfirmation("Do it")))
      contentAsJson(r) must_== Json.toJson(expected)
    }
  }
}
