package integration.admin

import config.ServiceConfig
import helpers._
import models._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.{Application, Configuration}

import java.time.Instant
import java.time.temporal.ChronoUnit


class HarvestConfigsSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser

  private val hcRoutes = controllers.datasets.routes.HarvestConfigs
  private def config(implicit app: Application): Configuration = app.injector.instanceOf[Configuration]

  private def testConfig(implicit app: Application): OaiPmhConfig = {
    val serviceConfig = ServiceConfig("ehridata", config)
      // where we're harvesting from:
      OaiPmhConfig(
        s"${serviceConfig.baseUrl}/oaipmh",
        "ead",
        Some("nl:r1"),
        from = Some(Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.SECONDS)),
        auth = serviceConfig.credentials.map { case (u, pw) => BasicAuthConfig(u, pw)})
  }


  "HarvestConfigs API" should {
    "save oaipmh configs" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = OaiPmhConfig("https://foo.bar/baz", "oai_ead", Some("test"), from = Some(Instant.now().minusSeconds(3600)))
      val r = FakeRequest(hcRoutes.save("r1", "oaipmh_test")).withUser(privilegedUser).callWith(Json.toJson(c))
      contentAsJson(r) must_== Json.toJson(c)
    }

    "error when saving invalid oaipmh configs" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = Json.obj("url" -> "https://foo.bar/baz", "format" -> "oai_ead", "set" -> None, "from" -> "2020-02-30T00:00:00Z" )
      val r = FakeRequest(hcRoutes.save("r1", "oaipmh_test")).withUser(privilegedUser).callWith(c)
      contentAsJson(r) must_== Json.parse("""{"error":"unable to parse config for type 'oaipmh' /from: Iso date value expected"}""")
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

    "error when saving invalid urlset configs" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = UrlSetConfig(Seq("https://foo.bar/baz" -> "foo.xml\r")) // devious LF
      val r = FakeRequest(hcRoutes.save("r1", "urlset_test")).withUser(privilegedUser).callWith(Json.toJson(c))
      contentAsJson(r) must_== Json.parse("""{"error":"unable to parse config for type 'urlset' /urlMap: Invalid file name"}""")
    }

    "error when saving configs to datasets with a different type" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = OaiPmhConfig("https://foo.bar/baz", "oai_ead", Some("test"))
      val r = FakeRequest(hcRoutes.save("r1", "default")).withUser(privilegedUser).callWith(Json.toJson(c))
      status(r) must_== BAD_REQUEST
    }

    "test oaipmh configs" in new DBTestApp("import-dataset-fixtures.sql") {
      val c = testConfig(app)
      val r = FakeRequest(hcRoutes.test("r1", "oaipmh_test")).withUser(privilegedUser).callWith(Json.toJson(c))
      contentAsJson(r) must_== Json.parse("""{"name":"EHRI","url":"http://example.com","version":"2.0","granularity":"YYYY-MM-DDThh:mm:ssZ"}""")
    }
  }
}
