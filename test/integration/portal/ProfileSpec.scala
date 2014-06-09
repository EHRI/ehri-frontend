package integration.portal

import scala.concurrent.ExecutionContext.Implicits.global
import helpers.Neo4jRunnerSpec
import models._
import play.api.test.FakeRequest
import backend.ApiUser
import mocks.MockBufferedMailer
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.MultipartFormData
import play.api.i18n.Messages


class ProfileSpec extends Neo4jRunnerSpec(classOf[ProfileSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  private val profileRoutes = controllers.portal.routes.Profile

  override def getConfig = Map(
    "recaptcha.skip" -> true,
    "ehri.signup.timeCheckSeconds" -> -1
  )

  "Portal views" should {
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

  "Signup process" should {

    val testEmail: String = "test@example.com"
    val testName: String = "Test Name"
    val data: Map[String,Seq[String]] = Map(
      SignupData.NAME -> Seq(testName),
      SignupData.EMAIL -> Seq(testEmail),
      SignupData.PASSWORD -> Seq("testpass"),
      SignupData.CONFIRM -> Seq("testpass"),
      SignupData.TIMESTAMP -> Seq(org.joda.time.DateTime.now.toString),
      SignupData.BLANK_CHECK -> Seq(""),
      SignupData.AGREE_TERMS -> Seq(true.toString),
      CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
    )

    "create a validation token and send a mail on signup" in new FakeApp {
      val numSentMails = MockBufferedMailer.mailBuffer.size
      val numAccounts = mocks.userFixtures.size
      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), data).get
      status(signup) must equalTo(SEE_OTHER)
      MockBufferedMailer.mailBuffer.size must beEqualTo(numSentMails + 1)
      MockBufferedMailer.mailBuffer.last.to must contain(testEmail)
      mocks.userFixtures.size must equalTo(numAccounts + 1)
      val userOpt = mocks.userFixtures.values.find(u => u.email == testEmail)
      userOpt must beSome.which { user =>
        user.verified must beFalse
      }
    }

    "prevent signup with mismatched passwords" in new FakeApp {
      val badData = data
        .updated(SignupData.CONFIRM, Seq("blibblob"))
      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(Messages("portal.signup.badPasswords"))
    }

    "prevent signup with invalid time diff" in new FakeApp(specificConfig = Map("ehri.signup.timeCheckSeconds" -> 5)) {
      val badData = data
        .updated(SignupData.TIMESTAMP, Seq(org.joda.time.DateTime.now.toString()))
      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(Messages("portal.signup.badTimestamp"))

      val badData2 = data
        .updated(SignupData.TIMESTAMP, Seq("bad-date"))
      val signup2 = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData2).get
      status(signup2) must equalTo(BAD_REQUEST)
      contentAsString(signup2) must contain(Messages("portal.signup.badTimestamp"))
    }

    "prevent signup with filled blank field" in new FakeApp {
      val badData = data.updated(SignupData.BLANK_CHECK, Seq("iAmARobot"))
      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(Messages("portal.signup.badSignupInput"))
    }

    "prevent signup where terms are not agreed" in new FakeApp {
      val badData = data.updated(SignupData.AGREE_TERMS, Seq(false.toString))
      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(Messages("portal.signup.agreeTerms"))
    }

    "allow unverified user to log in" in new FakeApp {
      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), data).get
      status(signup) must equalTo(SEE_OTHER)
      mocks.userFixtures.find(_._2.email == testEmail) must beSome.which { case(uid, u) =>
        // Ensure we can log in and view our profile
        val index = route(fakeLoggedInHtmlRequest(u, GET,
          profileRoutes.profile().url)).get
        status(index) must equalTo(OK)
        contentAsString(index) must contain(testName)
      }
    }
  }
}
