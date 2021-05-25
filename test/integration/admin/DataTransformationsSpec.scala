package integration.admin

import akka.util.ByteString
import helpers._
import models.DataTransformation.TransformationType
import models.{FileStage, _}
import play.api.http.{ContentTypes, HeaderNames, Writeable}
import play.api.libs.json.Json
import play.api.mvc.{Headers, Result}
import play.api.test.FakeRequest

import scala.concurrent.Future


class DataTransformationsSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser

  private val dtRoutes = controllers.datasets.routes.DataTransformations

  // Mock user who belongs to admin
  val userProfile = UserProfile(
    data = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name = "test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name = "Administrators")))
  )

  private val testPayload = ByteString(
    """<ead>
      |  <eadheader>
      |    <eadid>test-id</eadid>
      |  </eadheader>
      |</ead>
      |""".stripMargin)
  private val repoId = "r1"
  private val datasetId = "default"
  private val stage = FileStage.Input
  private val testFileName = "test.xml"


  private implicit val writeBytes: Writeable[ByteString] = new Writeable[ByteString](s => s, Some(ContentTypes.XML))

  private def putFile()(implicit app: play.api.Application): Future[Result] = {
    FakeRequest(controllers.datasets.routes.ImportFiles.uploadStream(repoId, datasetId, stage, testFileName))
      .withHeaders(Headers(
        HeaderNames.CONTENT_TYPE -> ContentTypes.XML,
        HeaderNames.HOST -> "localhost"
      ))
      .withUser(privilegedUser)
      .callWith(testPayload)
  }

  "Data Transformation API" should {

    "convert files" in new ITestApp {
      await(putFile())
      val map = resourceAsString("simple-mapping.tsv")
      val r = FakeRequest(dtRoutes.convertFile(repoId, datasetId, stage, testFileName))
        .withUser(privilegedUser)
        .callWith(Json.toJson(ConvertSpec(Seq(TransformationType.XQuery -> map))))

      status(r) must_== OK
      contentType(r) must beSome("text/xml")
      contentAsString(r) must contain("test-id-EHRI")
    }

    "convert files with correct errors" in new ITestApp {
      await(putFile())
      val map = resourceAsString("simple-mapping.tsv") + "/ead/\t@foobar\t/blah\t&\n" // invalid junk
      val r = FakeRequest(dtRoutes.convertFile(repoId, datasetId, stage, testFileName))
        .withUser(privilegedUser)
        .callWith(Json.toJson(ConvertSpec(Seq(TransformationType.XQuery -> map))))

      status(r) must_== BAD_REQUEST
      contentAsString(r) must contain("at /ead: at /ead/eadheader: Expecting valid step.")
    }
  }
}
