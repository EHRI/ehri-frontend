package integration.api.v1

import helpers.TestConfiguration
import play.api.http.ContentTypes
import play.api.libs.json.{JsDefined, JsString}
import play.api.test.{FakeRequest, PlaySpecification}

class ApiV1QuickSpec extends PlaySpecification with TestConfiguration {

  private val apiHomeRoutes = controllers.api.v1.routes.ApiV1Home

  "API/V1" should {
    "provide docs when requested as HTML" in new ITestApp {
      val docs = FakeRequest(apiHomeRoutes.index())
          .withHeaders(ACCEPT -> ContentTypes.HTML).call()
      contentAsString(docs) must contain(message("api.v1.header"))
      contentType(docs) must beSome("text/html")
    }

    "give Json-API schema version on index" in new ITestApp {
      val idx = FakeRequest(apiHomeRoutes.index())
          .withHeaders(ACCEPT -> ContentTypes.JSON).call()
      status(idx) must_== OK
      contentAsJson(idx) \ "jsonapi" \ "version" must_== JsDefined(
        JsString(app.configuration.get[String]("ehri.api.v1.version")))
    }

    "report status" in new ITestApp {
      val idx = FakeRequest(apiHomeRoutes.index())
        .withHeaders(ACCEPT -> ContentTypes.JSON).call()
      contentAsJson(idx) \ "meta" \ "status" must_== JsDefined(
        JsString(app.configuration.get[String]("ehri.api.v1.status")))
    }
  }
}
