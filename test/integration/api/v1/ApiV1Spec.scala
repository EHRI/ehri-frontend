package integration.api.v1

import helpers.{IntegrationTestRunner, TestConfiguration}
import models.api.v1.JsonApiV1
import org.everit.json.schema.{ValidationException, SchemaException}
import org.everit.json.schema.loader.SchemaLoader
import org.json.{JSONTokener, JSONObject}
import play.api.http.HeaderNames
import play.api.libs.json.{Json, JsValue, JsDefined, JsString}
import play.api.test.{FakeRequest, PlaySpecification}

class ApiV1Spec extends IntegrationTestRunner {

  private val apiRoutes = controllers.api.v1.routes.ApiV1

  override def getConfig = Map("ehri.api.v1.authorization.enabled" -> false)

  def validateJson(json: JsValue) = {
    val is = getClass.getResourceAsStream("/jsonapi-schema.json")
    try {
      val rawSchema = new JSONObject(new JSONTokener(is))
      val schema = SchemaLoader.load(rawSchema)
      schema.validate(new JSONObject(Json.prettyPrint(json)))
    } catch {
      case se: ValidationException =>
        import scala.collection.JavaConversions._
        println(se.getMessage)
        se.getCausingExceptions.toList.foreach { e =>
          println(" - " + e.getMessage)
        }
        throw se
    } finally {
      is.close()
    }
  }

  "API/V1" should {
    "forbid fetching protected items" in new ITestApp {
      val fetch = FakeRequest(apiRoutes.fetch("c1")).call()
      status(fetch) must_== FORBIDDEN
      validateJson(contentAsJson(fetch))
    }

    "allow fetching items" in new ITestApp {
      val fetch = FakeRequest(apiRoutes.fetch("c4")).call()
      status(fetch) must_== OK
      validateJson(contentAsJson(fetch))
      contentAsJson(fetch) \ "data" \ "attributes" \ "descriptions" \ 0 \
          "scopeAndContent" must_== JsDefined(JsString("Some description text for c4"))
    }

    "allow searching all items" in new ITestApp {
      val search = FakeRequest(apiRoutes.search()).call()
      status(search) must_== OK
      validateJson(contentAsJson(search))
    }

    "allow searching in items" in new ITestApp {
      val search = FakeRequest(apiRoutes.searchIn("r1")).call()
      status(search) must_== OK
      validateJson(contentAsJson(search))
    }

    "include context when search in items" in new ITestApp {
      val search = FakeRequest(GET, apiRoutes.searchIn("r1") + "?limit=1&page=2").call()
      status(search) must_== OK
      validateJson(contentAsJson(search))
      contentAsJson(search) \ "included" \ 0 \ "id" must_== JsDefined(JsString("r1"))
    }
  }
}
