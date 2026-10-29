package integration.admin

import helpers._
import models.{ContentTypes, ImportDataset, PermissionType}
import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import services.data.{AuthenticatedUser, DataUser}

import scala.concurrent.Future


class ImportDatasetsSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.{moderator, privilegedUser, unprivilegedUser}

  private val routes = controllers.datasets.routes.ImportDatasets

  private val repositoryUpdate = Map(ContentTypes.Repository.toString -> Seq(PermissionType.Update.toString))

  private def repoIds(r: Future[Result]): Seq[String] =
    contentAsJson(r).as[Seq[JsObject]].map(o => (o \ "repoId").as[String])

  "Import Dataset API" should {

    "list datasets for all repositories for admins" in new DBTestApp("import-dataset-fixtures.sql") {
      val r = FakeRequest(routes.listAll()).withUser(privilegedUser).call()
      status(r) must_== OK
      repoIds(r) must containTheSameElementsAs(Seq("r1", "r2"))
      // with each institution's country, to group them by
      contentAsJson(r).as[Seq[JsObject]].map(o => (o \ "repoId").as[String] -> (o \ "countryCode").as[String]).toMap must_==
        Map("r1" -> "nl", "r2" -> "gb")
    }

    "list datasets only for repositories a user can update" in new DBTestApp("import-dataset-fixtures.sql") {
      // reto can update r2, but not r1
      val dashboard = FakeRequest(routes.dashboard()).withUser(unprivilegedUser).call()
      status(dashboard) must_== OK
      val r = FakeRequest(routes.listAll()).withUser(unprivilegedUser).call()
      status(r) must_== OK
      repoIds(r) must_== Seq("r2")

      // linda cannot update any repository
      val none = FakeRequest(routes.listAll()).withUser(moderator).call()
      status(none) must_== OK
      repoIds(none) must beEmpty
    }

    "list datasets for all repositories for users with global update permission" in new DBTestApp("import-dataset-fixtures.sql") {
      implicit val apiUser: DataUser = AuthenticatedUser(privilegedUser.id)
      await(dataApi.setGlobalPermissions(moderator.id, repositoryUpdate))
      val r = FakeRequest(routes.listAll()).withUser(moderator).call()
      repoIds(r) must containTheSameElementsAs(Seq("r1", "r2"))
    }

    "list datasets for repositories in countries where a user has scoped update permission" in new DBTestApp("import-dataset-fixtures.sql") {
      // r2 is in gb, r1 in nl
      implicit val apiUser: DataUser = AuthenticatedUser(privilegedUser.id)
      await(dataApi.setScopePermissions(moderator.id, "gb", repositoryUpdate))
      val r = FakeRequest(routes.listAll()).withUser(moderator).call()
      repoIds(r) must_== Seq("r2")

      // ...and the listed repository's manager agrees that the user can use it
      status(FakeRequest(routes.manager("r2")).withUser(moderator).call()) must_== OK
    }

    "require a user for the dashboard, even when the site is unsecured" in new ITestApp(Map("ehri.secured" -> false)) {
      val r = FakeRequest(routes.dashboard()).call()
      status(r) must_== SEE_OTHER
    }

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
