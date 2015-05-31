package integration.portal

import java.io.File

import backend.AuthenticatedUser
import helpers.{FakeMultipartUpload, IntegrationTestRunner}
import models._
import org.apache.commons.io.FileUtils
import play.api.http.MimeTypes
import play.api.i18n.Messages
import play.api.libs.json.JsObject


class UserProfilesSpec extends IntegrationTestRunner with FakeMultipartUpload {
  import mocks.privilegedUser

  private val profileRoutes = controllers.portal.users.routes.UserProfiles
  private val portalRoutes = controllers.portal.routes.Portal

  private def getProfileImage: File = {
    // Bit dodgy here, upload the project logo as our profile image
    val tmpFile = java.io.File.createTempFile("anImage", ".png")
    tmpFile.deleteOnExit()
    FileUtils.copyFile(new File("modules/portal/public/img/logo.png"), tmpFile)
    tmpFile
  }

  "Portal views" should {
    "allow watching and unwatching items" in new ITestApp {
      val watch = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.watchItemPost("c1")), "").get
      status(watch) must equalTo(SEE_OTHER)

      // Watched items show up on the profile - maybe change this?
      val watching = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.watching())).get
      // Check the following page contains a link to the user we just followed
      contentAsString(watching) must contain(
        controllers.portal.routes.DocumentaryUnits.browse("c1").url)

      // Unwatch
      val unwatch = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.unwatchItemPost("c1")), "").get
      status(unwatch) must equalTo(SEE_OTHER)

      val watching2 = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.watching())).get
      // Check the profile contains no links to the item we just unwatched
      contentAsString(watching2) must not contain controllers.portal.routes.DocumentaryUnits.browse("c1").url

    }
    
    "allow fetching watched items as text, JSON, or CSV" in new ITestApp {
      import controllers.DataFormat
      val watch = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.watchItemPost("c1")), "").get
      status(watch) must equalTo(SEE_OTHER)

      val watchingText = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.watching(format = DataFormat.Text))).get
      contentType(watchingText)  must beSome.which { ct =>
        ct must be equalTo MimeTypes.TEXT.toString
      }
      
      val watchingJson = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.watching(format = DataFormat.Json))).get
      contentType(watchingJson)  must beSome.which { ct =>
        ct must be equalTo MimeTypes.JSON.toString
      }
      contentAsJson(watchingJson).validate[Seq[JsObject]].asOpt must beSome
      
      val watchingCsv = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.watching(format = DataFormat.Csv))).get
      println(contentAsString(watchingCsv))
      contentType(watchingCsv)  must beSome.which { ct =>
        ct must be equalTo "text/csv"
      }
    }

    "allow viewing profile" in new ITestApp {
      val prof = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.profile())).get
      status(prof) must equalTo(OK)
    }       

    "allow editing profile" in new ITestApp {
      val testName = "Inigo Montoya"
      val testInterest = "swords"
      val data = Map(
        "identifier" -> Seq("???"), // Overridden...
        UserProfileF.NAME -> Seq(testName),
        UserProfileF.INTERESTS -> Seq(testInterest)
      )
      val update = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.updateProfilePost()), data).get
      status(update) must equalTo(SEE_OTHER)

      val prof = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.profile())).get
      status(prof) must equalTo(OK)
      contentAsString(prof) must contain(testName)
      contentAsString(prof) must contain(testInterest)
    }

    "prevent script injection" in new ITestApp {
      val testName = "Evil User"
      val script = "<script>alert(\"Hello...\");</script>"
      val testInstitution = "Nefarious"
      val data = Map(
        UserProfileF.NAME -> Seq(testName),
        UserProfileF.INSTITUTION -> Seq(testInstitution + script)
      )
      val update = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.updateProfilePost()), data).get
      status(update) must equalTo(SEE_OTHER)

      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, profileRoutes.profile())).get
      status(prof) must equalTo(OK)
      contentAsString(prof) must contain(testName)
      contentAsString(prof) must contain(testInstitution)
      contentAsString(prof) must not contain script
      val search = route(fakeLoggedInHtmlRequest(privilegedUser,
        controllers.portal.social.routes.Social.browseUsers())).get
      status(search) must equalTo(OK)
      contentAsString(search) must contain(testName)
      contentAsString(search) must contain(testInstitution)
      contentAsString(search) must not contain script
    }

    "not allow uploading non-image files as profile image" in new ITestApp {
      val tmpFile = java.io.File.createTempFile("notAnImage", ".txt")
      FileUtils.write(tmpFile, "Hello, world\n")
      tmpFile.deleteOnExit()
      val result = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.updateProfileImagePost())
          .withFileUpload("image", tmpFile, "image/png")).get
      status(result) must equalTo(BAD_REQUEST)
      contentAsString(result) must contain(Messages("errors.badFileType"))
    }

    "allow uploading image files as profile image" in new ITestApp {
      val result = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.updateProfileImagePost())
        .withFileUpload("image", getProfileImage, "image/png")).get
      status(result) must equalTo(SEE_OTHER)
      storedFileBuffer.lastOption must beSome.which { f =>
        f.toString must endingWith(s"${privilegedUser.id}.png")
      }
    }

    "prevent uploading files that are too large" in new ITestApp(
        Map("ehri.portal.profile.maxImageSize" -> 10)) {
      val result = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.updateProfileImagePost())
        .withFileUpload("image", getProfileImage, "image/png")).get
      status(result) must equalTo(REQUEST_ENTITY_TOO_LARGE)
      contentAsString(result) must contain(Messages("errors.imageTooLarge"))
    }

    "allow deleting profile with correct confirmation" in new ITestApp {
      // Fetch the current name
      implicit val apiUser = AuthenticatedUser(privilegedUser.id)
      val cname = await(testBackend.get[UserProfile](privilegedUser.id)).model.name
      val data = Map("confirm" -> Seq(cname))
      val delete = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.deleteProfilePost()), data).get
      status(delete) must equalTo(SEE_OTHER)

      // Check user has been anonymised...
      val cnameAfter = await(testBackend.get[UserProfile](privilegedUser.id)).model.name
      cname must not equalTo cnameAfter
      // The account should be completely deleted...
      await(mockAccounts.findById(privilegedUser.id)) must beNone
    }

    "disallow deleting profile without correct confirmation" in new ITestApp {
      val data = Map("confirm" -> Seq("THE WRONG CONFIRMATION"))
      val delete = route(fakeLoggedInHtmlRequest(privilegedUser,
        profileRoutes.deleteProfilePost()), data).get
      status(delete) must equalTo(BAD_REQUEST)
    }
  }
}
