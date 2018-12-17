package integration.api.graphql

import helpers.IntegrationTestRunner
import play.api.libs.json._
import play.api.test.FakeRequest


class GraphQLSpec extends IntegrationTestRunner {

  private val graphQLRoutes = controllers.api.graphql.routes.GraphQL

  val query: String = """{Country(id: "gb") {name}}"""
  val queryObj: JsObject = Json.obj(
    "query" -> query,
    "variables" -> JsNull
  )

  "GraphQL API" should {
    "respond to text/plain queries" in new ITestApp {
      val r = FakeRequest(graphQLRoutes.query()).callWith(query)
      status(r) must_== OK
      (contentAsJson(r) \ "data" \ "Country" \ "name") must_== JsDefined(JsString("United Kingdom"))
    }

    "respond to application/json queries" in new ITestApp {
      val r = FakeRequest(graphQLRoutes.query()).callWith(queryObj)
      status(r) must_== OK
      (contentAsJson(r) \ "data" \ "Country" \ "name") must_== JsDefined(JsString("United Kingdom"))
    }
  }
}
