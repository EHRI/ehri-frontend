package integration.admin

import akka.util.ByteString
import controllers.institutions.FileToUpload
import helpers._
import models._
import play.api.http.{ContentTypes, HeaderNames, MimeTypes, Writeable}
import play.api.libs.json.Json
import play.api.mvc.{Headers, Result}
import play.api.test.FakeRequest
import services.storage.FileList

import scala.concurrent.Future


class RepositoryDataSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  private val repoDataRoutes = controllers.institutions.routes.RepositoryData

  // Mock user who belongs to admin
  val userProfile = UserProfile(
    data = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name = "test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name = "Administrators")))
  )

  private val testPayload = ByteString("<xml>Hello, world</xml>")
  private val testPrefix = "https://ehri-assets.mystorage.com/localhost/ingest/r1/"
  private val testFileName = "test.xml"

  private implicit val writeBytes: Writeable[ByteString] = new Writeable[ByteString](s => s, Some(ContentTypes.XML))

  private def putFile()(implicit app: play.api.Application): Future[Result] = {
    FakeRequest(repoDataRoutes.uploadStream("r1", testFileName))
      .withHeaders(Headers(
        HeaderNames.CONTENT_TYPE -> ContentTypes.XML,
        HeaderNames.HOST -> "localhost"
      ))
      .withUser(privilegedUser)
      .callWith(testPayload)
  }

  "Repository Data API" should {

    "provide PUT urls" in new ITestApp {
      val r = FakeRequest(repoDataRoutes.uploadHandle("r1")).withUser(privilegedUser).callWith(
        Json.toJson(FileToUpload(
          name = testFileName,
          `type` = ContentTypes.XML,
          size = testPayload.size
        )))

      contentAsJson(r) must_== Json.parse("{\"presignedUrl\":\"" + testPrefix + testFileName + "\"}")
    }

    "upload via server" in new ITestApp {
      val r = putFile()
      contentAsJson(r) must_== Json.parse("{\"url\":\"" + testPrefix + testFileName + "\"}")
    }

    "fetch data" in new ITestApp {
      await(putFile())
      val r = FakeRequest(repoDataRoutes.download("r1", testFileName))
        .withUser(privilegedUser)
        .call()

      contentAsBytes(r) must_== testPayload
      contentType(r) must beSome(MimeTypes.XML)
    }

    "list files" in new ITestApp {
      await(putFile())
      val r = FakeRequest(repoDataRoutes.listFiles("r1"))
        .withUser(privilegedUser)
        .call()

      contentType(r) must beSome(MimeTypes.JSON)
      val list = contentAsJson(r).as[FileList]
      list.files.map(_.key) must contain(testFileName)
    }

    "delete files" in new ITestApp {
      await(putFile())
      val r = FakeRequest(repoDataRoutes.deleteFiles("r1"))
        .withHeaders(Headers(
          HeaderNames.HOST -> "localhost"
        ))
        .withUser(privilegedUser)
        .callWith(Json.arr(testFileName))

      contentAsJson(r) must_== Json.arr(testFileName)
    }

    "validate files" in new ITestApp {
      val r = FakeRequest(repoDataRoutes.validateFiles("r1"))
        .withHeaders(Headers(
          HeaderNames.HOST -> "localhost"
        ))
        .withUser(privilegedUser)
        .callWith(Json.arr(testFileName))

      // Currently this will simply fail this an error because
      // we can't access the mocked files via URI in the test
      // environment
      contentAsJson(r) must_== Json.obj("error" -> "ehri-assets.mystorage.com")
    }
  }
}
