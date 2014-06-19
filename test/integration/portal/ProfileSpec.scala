package integration.portal

import scala.concurrent.ExecutionContext.Implicits.global
import helpers.Neo4jRunnerSpec
import models._
import backend.ApiUser
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.MultipartFormData
import play.api.http.MimeTypes
import play.api.libs.json.JsObject


class ProfileSpec extends Neo4jRunnerSpec(classOf[ProfileSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  private val profileRoutes = controllers.portal.routes.Profile
  private val portalRoutes = controllers.portal.routes.Portal

  "Portal views" should {
    "allow watching and unwatching items" in new FakeApp {
      val watch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.watchItemPost("c1").url), "").get
      status(watch) must equalTo(SEE_OTHER)

      // Watched items show up on the profile - maybe change this?
      val watching = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.watching().url)).get
      // Check the following page contains a link to the user we just followed
      contentAsString(watching) must contain(
        portalRoutes.browseDocument("c1").url)

      // Unwatch
      val unwatch = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.unwatchItemPost("c1").url), "").get
      status(unwatch) must equalTo(SEE_OTHER)

      val watching2 = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.watching().url)).get
      // Check the profile contains no links to the item we just unwatched
      contentAsString(watching2) must not contain portalRoutes.browseDocument("c1").url

    }
    
    "allow fetching watched items as text, JSON, or CSV" in new FakeApp {
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

    "allow viewing profile" in new FakeApp {
      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.profile().url)).get
      status(prof) must equalTo(OK)
    }       

    "allow editing profile" in new FakeApp {
      val testName = "Inigo Montoya"
      val data = Map(
        "identifier" -> Seq("???"), // Overridden...
        UserProfileF.NAME -> Seq(testName)
      )
      val update = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.updateProfilePost().url), data).get
      status(update) must equalTo(SEE_OTHER)

      val prof = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.profile().url)).get
      status(prof) must equalTo(OK)
      contentAsString(prof) must contain(testName)
    }


    "not allow uploading non-image files as profile image" in new FakeApp {
      val tmpFile = java.io.File.createTempFile("notAnImage", ".txt")
      val data = new MultipartFormData(Map(), List(
        FilePart("image", "message", Some("Content-Type: multipart/form-data"),
          play.api.libs.Files.TemporaryFile(tmpFile))
      ), List(), List())

      val result = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.updateProfileImagePost().url), data.asFormUrlEncoded).get
      status(result) must equalTo(BAD_REQUEST)
      // TODO: Verifty types of BAD_REQUEST
      //contentAsString(result) must contain(Messages("portal.errors.badFileType"))
    }

    "allow deleting profile with correct confirmation" in new FakeApp {
      // Fetch the current name
      implicit val apiUser = ApiUser(Some(privilegedUser.id))
      val cname = await(testBackend.get[UserProfile](privilegedUser.id)).model.name
      val data = Map("confirm" -> Seq(cname))
      val delete = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.deleteProfilePost().url), data).get
      status(delete) must equalTo(SEE_OTHER)

      // Check user has been anonymised...
      val cnameAfter = await(testBackend.get[UserProfile](privilegedUser.id)).model.name
      cname must not equalTo cnameAfter
    }

    "disallow deleting profile without correct confirmation" in new FakeApp {
      val data = Map("confirm" -> Seq("THE WRONG CONFIRMATION"))
      val delete = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        profileRoutes.deleteProfilePost().url), data).get
      status(delete) must equalTo(BAD_REQUEST)
    }

    "redirect to index page on log out" in new FakeApp {
      val logout = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        profileRoutes.logout().url)).get
      status(logout) must equalTo(SEE_OTHER)
      flash(logout).get("success") must beSome.which { fl =>
        // NB: No i18n here...
        fl must contain("portal.logout.confirmation")
      }
    }
  }
}
