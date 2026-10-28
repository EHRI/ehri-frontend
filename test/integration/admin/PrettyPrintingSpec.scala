package integration.admin

import helpers._
import mockdata.{privilegedUser, unprivilegedUser}
import models.FileStage
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
        .withUser(privilegedUser)
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
        .withUser(privilegedUser)
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
        .withUser(privilegedUser)
        .callWith(csv)
      contentAsString(res) must_== csv
    }

    "deny access to unauthenticated users" in new ITestApp() {
      val get = FakeRequest(ppRoutes.reformat("r1", "default", FileStage.Input, "test.xml", None))
        .call()
      status(get) must_== SEE_OTHER
      val post = FakeRequest(ppRoutes.reformatPost("r1"))
        .callWith(Json.obj("foo" -> "bar"))
      status(post) must_== SEE_OTHER
    }

    "deny access to users without update permission on the repository" in new ITestApp() {
      val get = FakeRequest(ppRoutes.reformat("r1", "default", FileStage.Input, "test.xml", None))
        .withUser(unprivilegedUser)
        .call()
      status(get) must_== FORBIDDEN
      val post = FakeRequest(ppRoutes.reformatPost("r1"))
        .withUser(unprivilegedUser)
        .callWith(Json.obj("foo" -> "bar"))
      status(post) must_== FORBIDDEN
    }
  }
}
