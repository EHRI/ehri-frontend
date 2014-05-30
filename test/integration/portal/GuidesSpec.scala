package integration.portal

import helpers.{WithSqlFile, Neo4jRunnerSpec}
import play.api.test.FakeRequest


class GuidesSpec extends Neo4jRunnerSpec(classOf[GuidesSpec]) {

  private val guideRoutes = controllers.portal.routes.Guides

  override def getConfig = Map("recaptcha.skip" -> true)

  "Guide views" should {
    "show index page for fixture guide" in new WithSqlFile("guide-fixtures.sql") {
      val doc = route(FakeRequest(GET, guideRoutes.home("terezin").url)).get
      status(doc) must equalTo(OK)
    }

    "show 404 for bad path" in new WithSqlFile("guide-fixtures.sql") {
      val doc = route(FakeRequest(GET, guideRoutes.home("BAD").url)).get
      status(doc) must equalTo(NOT_FOUND)
    }
  }
}
