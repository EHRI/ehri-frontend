package integration.admin

import helpers._
import models.ImportDataset
import play.api.libs.json.{JsBoolean, JsString, Json}
import play.api.test.FakeRequest


class ImportDatasetsSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser

  private val routes = controllers.datasets.routes.ImportDatasets

  "Import Dataset API" should {

    "list items" in new DBTestApp("import-dataset-fixtures.sql") {
      val r = FakeRequest(routes.list("r1")).withUser(privilegedUser).call()
      status(r) must_== OK
      contentAsJson(r).apply(0)("repoId") must_== JsString("r1")
    }

    "save items" in new DBTestApp("import-dataset-fixtures.sql") {
      val data = Json.obj(
        "id" -> "test",
        "src" -> ImportDataset.Src.Upload,
        "name" -> "Test",
        "fonds" -> None,
        "notes" -> None,
        "sync" -> false,
        "nest" -> false,
        "setHierarchy" -> false,
        "status" -> ImportDataset.Status.Active,
      )
      val r = FakeRequest(routes.create("r1")).withUser(privilegedUser).callWith(data)
      status(r) must_== CREATED
      contentAsJson(r).apply("repoId") must_== JsString("r1")
    }

    "update items" in new DBTestApp("import-dataset-fixtures.sql") {
      val data = Json.obj(
        "id" -> "default",
        "src" -> ImportDataset.Src.Upload,
        "name" -> "Test",
        "fonds" -> None,
        "notes" -> None,
        "sync" -> true,
        "nest" -> false,
        "setHierarchy" -> false,
        "status" -> ImportDataset.Status.Active,
      )
      val r = FakeRequest(routes.update("r1", "default")).withUser(privilegedUser).callWith(data)
      status(r) must_== OK
      contentAsJson(r).apply("sync") must_== JsBoolean(true)
    }

    "prevent using invalid fonds id" in new DBTestApp("import-dataset-fixtures.sql") {
      val data = Json.obj(
        "id" -> "default",
        "src" -> ImportDataset.Src.Upload,
        "name" -> "Test",
        "fonds" -> "nope",
        "notes" -> None,
        "sync" -> false,
        "nest" -> false,
        "setHierarchy" -> false,
        "status" -> ImportDataset.Status.Active,
      )
      val r = FakeRequest(routes.update("r1", "default")).withUser(privilegedUser).callWith(data)
      status(r) must_== BAD_REQUEST
      contentAsJson(r) must_== Json.obj("error" -> "Fonds 'nope' not found")
    }

    "delete items" in new DBTestApp("import-dataset-fixtures.sql") {
      val r = FakeRequest(routes.delete("r1", "default")).withUser(privilegedUser).call()
      status(r) must_== NO_CONTENT
    }
  }
}
