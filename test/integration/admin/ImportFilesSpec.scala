package integration.admin

import org.apache.pekko.util.ByteString
import controllers.datasets.FileToUpload
import helpers._
import models.{FileStage, _}
import org.apache.commons.codec.digest.DigestUtils
import play.api.http.{ContentTypes, HeaderNames, MimeTypes, Writeable}
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.mvc.{Headers, Result}
import play.api.test.FakeRequest
import services.storage.{FileList, FileMeta}

import scala.concurrent.Future


class ImportFilesSpec extends IntegrationTestRunner with ResourceUtils {

  import mockdata.privilegedUser

  private val repoDataRoutes = controllers.datasets.routes.ImportFiles

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
  private val fileStage = FileStage.Input
  private val testFileName = "test.xml"

  private def testFilePath(implicit app: play.api.Application): String = {
    val bucket = app.configuration.get[String]("storage.dam.classifier")
    val host = app.configuration.get[String]("storage.dam.config.endpoint-url")
    val testPrefix = s"$host/$bucket/$hostInstance/ingest-data/$repoId/$datasetId/$fileStage/"
    testPrefix + testFileName
  }

  private implicit val writeBytes: Writeable[ByteString] = new Writeable[ByteString](s => s, Some(ContentTypes.XML))

  private def putFile(name: String, payload: ByteString = testPayload, ds: String = datasetId, stage: FileStage.Value = fileStage)(
    implicit app: play.api.Application): Future[Result] = {

    FakeRequest(repoDataRoutes.uploadStream(repoId, ds, stage, name))
      .withHeaders(Headers(
        HeaderNames.CONTENT_TYPE -> ContentTypes.XML,
        HeaderNames.HOST -> "localhost"
      ))
      .withUser(privilegedUser)
      .callWith(payload)
  }

  private def putFileNoMultiPart(name: String, payload: ByteString = testPayload, ds: String = datasetId, stage: FileStage.Value = fileStage)(
      implicit app: play.api.Application): Future[WSResponse] = {
    val ws = app.injector.instanceOf[play.api.libs.ws.WSClient]

    val r = FakeRequest(repoDataRoutes.uploadHandle(repoId, ds, stage))
      .withUser(privilegedUser)
      .callWith(Json.toJson(FileToUpload(
        name = name,
        `type` = ContentTypes.XML,
        size = payload.size
      )))
    val data = contentAsJson(r).as[Map[String, String]]
    val uri = data("presignedUrl")
    ws.url(uri).withHttpHeaders(CONTENT_TYPE -> ContentTypes.XML).put(payload)
  }

  "Import Files API" should {

    "provide PUT urls" in new ITestApp {
      val r = FakeRequest(repoDataRoutes.uploadHandle(repoId, datasetId, fileStage)).withUser(privilegedUser).callWith(
        Json.toJson(FileToUpload(
          name = testFileName,
          `type` = ContentTypes.XML,
          size = testPayload.size
        )))

      val data: Option[Map[String,String]] = contentAsJson(r).validate[Map[String,String]].asOpt
      data must beSome
        .which((_:Map[String,String]).get("presignedUrl") must beSome
        .which((_:String) must startWith(testFilePath)))
    }

    "upload via server" in new ITestApp {
      val r = putFile(testFileName)
      contentAsJson(r) must_== Json.parse("{\"url\":\"" + testFilePath + "\"}")
    }

    "fetch data" in new ITestApp {
      await(putFile(testFileName))
      val r = FakeRequest(repoDataRoutes.download(repoId, datasetId, fileStage, testFileName))
        .withUser(privilegedUser)
        .call()

      contentAsBytes(r) must_== testPayload
      contentType(r) must beSome(MimeTypes.XML)
    }

    "list files" in new ITestApp {
      await(putFile(testFileName))
      val r = FakeRequest(repoDataRoutes.listFiles(repoId, datasetId, fileStage))
        .withUser(privilegedUser)
        .call()

      contentType(r) must beSome(MimeTypes.JSON)
      val list = contentAsJson(r).as[FileList]
      list.files.map(_.key) must contain(testFileName)
    }

    "get file info" in new ITestApp {
      await(putFile(testFileName))
      val r = FakeRequest(repoDataRoutes.info(repoId, datasetId, fileStage, testFileName))
        .withUser(privilegedUser)
        .call()

      contentType(r) must beSome(MimeTypes.JSON)
      val versions = (contentAsJson(r) \ "versions").as[Seq[FileMeta]]
      versions.map(_.key) must contain(testFileName)
    }

    "delete files" in new ITestApp {
      await(putFile(testFileName))
      await(putFile(testFileName + "2"))
      val r = FakeRequest(repoDataRoutes.deleteFiles(repoId, datasetId, fileStage))
        .withUser(privilegedUser)
        .callWith(Json.arr(testFileName))

      contentAsJson(r) must_== Json.obj("deleted" -> 1)
    }

    "delete all files" in new ITestApp {
      await(putFile(testFileName))
      await(putFile(testFileName + "2"))
      val r = FakeRequest(repoDataRoutes.deleteFiles(repoId, datasetId, fileStage))
        .withUser(privilegedUser)
        .callWith(Json.arr())

      contentAsJson(r) must_== Json.obj("deleted" -> 2)
    }

    "copy files in an idempotent manner" in new ITestApp {
      await(putFileNoMultiPart(testFileName, ds = "ds1", stage = FileStage.Config))
      val r = FakeRequest(repoDataRoutes.copyFile(repoId, "ds1", FileStage.Config, fileName = testFileName, toDs = "ds2"))
        .withUser(privilegedUser)
        .callWith(Json.arr(testFileName))
      status(r) must_== CREATED
      val data = contentAsJson(r).as[Map[String, String]]
      data.get("message") must beSome("File copied")

      val r2 = FakeRequest(repoDataRoutes.copyFile(repoId, "ds1", FileStage.Config, fileName = testFileName, toDs = "ds2"))
        .withUser(privilegedUser)
        .callWith(Json.arr(testFileName))
      status(r2) must_== OK
      val data2 = contentAsJson(r2).as[Map[String, String]]
      data2.get("message") must beSome("File with identical contents already exists")
    }

    "validate files" in new ITestApp {
      await(putFile(testFileName))
      val tag = DigestUtils.md5Hex(testPayload.utf8String)
      val r = FakeRequest(repoDataRoutes.validateFiles(repoId, datasetId, fileStage))
        .withUser(privilegedUser)
        .callWith(Json.obj(tag -> testFileName))

      // Validator will object because EAD without the namespace is not
      // recognised as EAD.
      contentAsJson(r) must_== Json.parse(
        s"""[{
          "key": "$testFileName",
          "eTag": "$tag",
          "errors": [{
            "line": 1,
            "pos": 6,
            "error": "element \\"ead\\" not allowed anywhere; expected element \\"ead\\" (with xmlns=\\"urn:isbn:1-931666-22-9\\")"
           }]
          }]"""
      )
    }

    "give JSON errors for invalid payloads" in new ITestApp {
      val r = FakeRequest(repoDataRoutes.validateFiles(repoId, datasetId, fileStage))
        .withUser(privilegedUser)
        .callWith(Json.arr("not", "an", "object"))

      status(r) must_== BAD_REQUEST
      contentAsJson(r) must_== Json.parse("{\"error\":\"invalid\",\"details\":{\"obj\":[{\"msg\":[\"error.expected.jsobject\"],\"args\":[]}]}}")
    }
  }
}
