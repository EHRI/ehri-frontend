package integration.api.v1

import controllers.api.v1.ApiV1
import helpers.TestConfiguration
import models.api.v1.JsonApiV1
import play.api.http.HeaderNames
import play.api.libs.json.{JsDefined, JsString}
import play.api.test.{FakeRequest, PlaySpecification}

class ApiV1QuickSpec extends PlaySpecification with TestConfiguration {

  private val apiRoutes = controllers.api.v1.routes.ApiV1

  "API/V1" should {
    "give Json-API schema version on index" in new ITestApp {
      val idx = FakeRequest(apiRoutes.index())
        .withHeaders(HeaderNames.CONTENT_TYPE -> JsonApiV1.JSONAPI_MIMETYPE)
        .call()
      status(idx) must_== OK
      contentAsJson(idx) \ "jsonapi" \ "version" must_== JsDefined(JsString("1.0"))
    }

    "give Not Acceptable with a modified Accept header" in new ITestApp {
      val idx = FakeRequest(apiRoutes.index())
        .withHeaders(HeaderNames.CONTENT_TYPE -> JsonApiV1.JSONAPI_MIMETYPE,
          HeaderNames.ACCEPT -> (JsonApiV1.JSONAPI_MIMETYPE + ";encoding=utf8"))
        .call()
      status(idx) must_== NOT_ACCEPTABLE
    }

    "give Unsupported Media Type without the Json-API mimetype" in new ITestApp {
      val idx = FakeRequest(apiRoutes.index()).call()
      status(idx) must_== UNSUPPORTED_MEDIA_TYPE
    }
  }
}
