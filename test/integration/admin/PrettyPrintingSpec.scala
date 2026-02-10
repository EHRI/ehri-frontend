package integration.admin

import helpers._
import play.api.libs.json.Json
import play.api.test.FakeRequest

/**
 * Spec to test various page views operate as expected.
 */
class PrettyPrintingSpec extends IntegrationTestRunner with FakeMultipartUpload {

  private val ppRoutes = controllers.datasets.routes.PrettyPrinting

  "PrettyPrinting" should {
    "allow reformatting JSON data" in new ITestApp() {
      val json = Json.parse("""{"foo": "bar", "baz": [1,2,3,4]}""")
      val res = FakeRequest(ppRoutes.reformatPost("r1"))
        .callWith(json)
      status(res) must_== OK
      contentAsString(res) must_==
        """{
          |  "foo" : "bar",
          |  "baz" : [ 1, 2, 3, 4 ]
          |}""".stripMargin
    }

    "allow reformatting XML data" in new ITestApp() {
      val xml = scala.xml.XML.loadString("""<ead><eadheader><eadid>1</eadid></eadheader></ead>""")
      val res = FakeRequest(ppRoutes.reformatPost("r1"))
        .callWith(xml)
      status(res) must_== OK
      contentAsString(res) must_==
        """<?xml version='1.0' encoding='UTF-8'?>
          |<ead>
          |    <eadheader>
          |        <eadid>1</eadid>
          |    </eadheader>
          |</ead>""".stripMargin
    }

    "return unhandled data intact" in new ITestApp() {
      val csv = "foo,bar,baz\n1,2,3"
      val res = FakeRequest(ppRoutes.reformatPost("r1"))
        .withHeaders("content-type" -> "text/csv")
        .callWith(csv)
      contentAsString(res) must_== csv
    }
  }
}
