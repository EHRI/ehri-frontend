package integration.portal

import helpers.IntegrationTestRunner
import models._
import backend.AuthenticatedUser
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.MultipartFormData
import play.api.http.MimeTypes
import play.api.libs.json.JsObject


class UserProfilesSpec extends IntegrationTestRunner {
  import mocks.privilegedUser

  private val profileRoutes = controllers.portal.users.routes.UserProfiles
  private val portalRoutes = controllers.portal.routes.Portal

  "Portal views" should {
    "allow watching and unwatching items" in new ITestApp {
      val watch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.watchItemPost("c1").url), "").get
      status(watch) must equalTo(SEE_OTHER)

      // Watched items show up on the profile - maybe change this?
      val watching = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.watching().url)).get
      // Check the following page contains a link to the user we just followed
      contentAsString(watching) must contain(
        controllers.portal.routes.DocumentaryUnits.browse("c1").url)

      // Unwatch
      val unwatch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.unwatchItemPost("c1").url), "").get
      status(unwatch) must equalTo(SEE_OTHER)

      val watching2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.watching().url)).get
      // Check the profile contains no links to the item we just unwatched
      contentAsString(watching2) must not contain controllers.portal.routes.DocumentaryUnits.browse("c1").url

    }
    
    "allow fetching watched items as text, JSON, or CSV" in new ITestApp {
      import controllers.DataFormat
      val watch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.watchItemPost("c1").url), "").get
      status(watch) must equalTo(SEE_OTHER)

      val watchingText = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.watching(format = DataFormat.Text).url)).get
      contentType(watchingText)  must beSome.which { ct =>
        ct must be equalTo MimeTypes.TEXT.toString
      }
      
      val watchingJson = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.watching(format = DataFormat.Json).url)).get
      contentType(watchingJson)  must beSome.which { ct =>
        ct must be equalTo MimeTypes.JSON.toString
      }
      contentAsJson(watchingJson).validate[Seq[JsObject]].asOpt must beSome
      
      val watchingCsv = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.watching(format = DataFormat.Csv).url)).get
      println(contentAsString(watchingCsv))
      contentType(watchingCsv)  must beSome.which { ct =>
        ct must be equalTo "text/csv"
      }
    }

    "allow viewing profile" in new ITestApp {
      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.profile().url)).get
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
      val update = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.updateProfilePost().url), data).get
      status(update) must equalTo(SEE_OTHER)

      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.profile().url)).get
      status(prof) must equalTo(OK)
      contentAsString(prof) must contain(testName)
      contentAsString(prof) must contain(testInterest)
    }

    "not allow uploading non-image files as profile image" in new ITestApp {
      val tmpFile = java.io.File.createTempFile("notAnImage", ".txt")
      val data = new MultipartFormData(Map(), List(
        FilePart("image", "message", Some("Content-Type: multipart/form-data"),
          play.api.libs.Files.TemporaryFile(tmpFile))
      ), List(), List())

      val result = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.updateProfileImagePost().url), data.asFormUrlEncoded).get
      status(result) must equalTo(BAD_REQUEST)
      // TODO: Verifty types of BAD_REQUEST
      //contentAsString(result) must contain(Messages("errors.badFileType"))
    }

    "allow deleting profile with correct confirmation" in new ITestApp {
      // Fetch the current name
      implicit val apiUser = AuthenticatedUser(privilegedUser.id)
      val cname = await(testBackend.get[UserProfile](privilegedUser.id)).model.name
      val data = Map("confirm" -> Seq(cname))
      val delete = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.deleteProfilePost().url), data).get
      status(delete) must equalTo(SEE_OTHER)

      // Check user has been anonymised...
      val cnameAfter = await(testBackend.get[UserProfile](privilegedUser.id)).model.name
      cname must not equalTo cnameAfter
      // The account should be completely deleted...
      await(mockAccounts.findById(privilegedUser.id)) must beNone
    }

    "disallow deleting profile without correct confirmation" in new ITestApp {
      val data = Map("confirm" -> Seq("THE WRONG CONFIRMATION"))
      val delete = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.deleteProfilePost().url), data).get
      status(delete) must equalTo(BAD_REQUEST)
    }
  }
}
