package integration.admin

import controllers.datasets.{CleanupConfirmation, CleanupSummary}
import helpers._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.ingest.Cleanup


class ImportLogsSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.{privilegedUser, unprivilegedUser}
  private val routes = controllers.datasets.routes.ImportLogs

  "Import Logs API" should {

    "get heuristic cleanup" in new DBTestApp("import-log-fixtures.sql") {
      val r = FakeRequest(routes.cleanup("r1", 1))
        .withUser(privilegedUser)
        .call()
      contentAsJson(r) must_== Json.toJson(Cleanup(
        deletions = Seq("nl-r1-m19", "c4"),
        redirects = Seq("nl-r1-m19" -> "nl-r1-test-m19")
      ))
    }

    "perform cleanup" in new DBTestApp("import-log-fixtures.sql") {
      val expected = CleanupSummary(
        // we delete both the deleted item and the moved item's source
        deletions = 2,
        // this would be 1 except the fixture item nl-r1-test-m19
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

    "deny cleanup to users without delete permission in the repository" in new DBTestApp("import-log-fixtures.sql") {
      // reto can update r2, which allows access to the dataset manager,
      // but has no permission to delete items within it
      val r = FakeRequest(routes.doCleanup("r2", 1))
        .withUser(unprivilegedUser)
        .callWith(Json.toJson(CleanupConfirmation("Do it")))
      status(r) must_== FORBIDDEN

      val async = FakeRequest(routes.doCleanupAsync("r2", 1))
        .withUser(unprivilegedUser)
        .callWith(Json.toJson(CleanupConfirmation("Do it")))
      status(async) must_== FORBIDDEN
    }

    "allow other snapshot operations to users with update permission" in new DBTestApp("import-log-fixtures.sql") {
      val r = FakeRequest(routes.listSnapshots("r2"))
        .withUser(unprivilegedUser)
        .call()
      status(r) must_== OK
    }
  }
}
