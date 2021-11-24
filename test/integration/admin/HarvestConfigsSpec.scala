package integration.admin

import helpers._
import models._
import play.api.libs.json.Json
import play.api.test.FakeRequest


class HarvestConfigsSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser

  private val hcRoutes = controllers.datasets.routes.HarvestConfigs

  // Mock user who belongs to admin
  private val userProfile = UserProfile(
    data = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name = "test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name = "Administrators")))
  )

  "HarvestConfigs API" should {
    "save oaipmh configs" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = OaiPmhConfig("https://foo.bar/baz", "oai_ead", Some("test"))
      val r = FakeRequest(hcRoutes.save("r1", "oaipmh_test")).withUser(privilegedUser).callWith(Json.toJson(c))
      contentAsJson(r) must_== Json.toJson(c)
    }

    "save resourcesync configs" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = ResourceSyncConfig("https://foo.bar/baz", filter = Some("foo"))
      val r = FakeRequest(hcRoutes.save("r1", "rs_test")).withUser(privilegedUser).callWith(Json.toJson(c))
      contentAsJson(r) must_== Json.toJson(c)
    }

    "save urlset configs" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = UrlSetConfig(Seq("https://foo.bar/baz" -> "foo.xml"))
      val r = FakeRequest(hcRoutes.save("r1", "urlset_test")).withUser(privilegedUser).callWith(Json.toJson(c))
      contentAsJson(r) must_== Json.toJson(c)
    }

    "error when saving configs to datasets with a different type" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = OaiPmhConfig("https://foo.bar/baz", "oai_ead", Some("test"))
      val r = FakeRequest(hcRoutes.save("r1", "default")).withUser(privilegedUser).callWith(Json.toJson(c))
      status(r) must_== BAD_REQUEST
    }
  }
}
