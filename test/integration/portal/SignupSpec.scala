package integration.portal

import helpers.Neo4jRunnerSpec
import models._
import play.api.test.FakeRequest
import mocks.MockBufferedMailer
import play.api.i18n.Messages


class SignupSpec extends Neo4jRunnerSpec(classOf[SignupSpec]) {

  private val profileRoutes = controllers.portal.routes.Profile

  override def getConfig = Map(
    "recaptcha.skip" -> true,
    "ehri.signup.timeCheckSeconds" -> -1
  )

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
      val numSentMails = mailBuffer.size
      val numAccounts = mocks.userFixtures.size
      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), data).get
      status(signup) must equalTo(SEE_OTHER)
      mailBuffer.size must beEqualTo(numSentMails + 1)
      mailBuffer.last.to must contain(testEmail)
      mocks.userFixtures.size must equalTo(numAccounts + 1)
      val userOpt = mocks.userFixtures.values.find(u => u.email == testEmail)
      userOpt must beSome.which { user =>
        user.verified must beFalse
      }
    }

//    "prevent signup with mismatched passwords" in new FakeApp {
//      val badData = data
//        .updated(SignupData.CONFIRM, Seq("blibblob"))
//      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
//        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
//      status(signup) must equalTo(BAD_REQUEST)
//      contentAsString(signup) must contain(Messages("portal.signup.badPasswords"))
//    }
//
//    "prevent signup with invalid time diff" in new FakeApp(specificConfig = Map("ehri.signup.timeCheckSeconds" -> 5)) {
//      val badData = data
//        .updated(SignupData.TIMESTAMP, Seq(org.joda.time.DateTime.now.toString()))
//      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
//        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
//      status(signup) must equalTo(BAD_REQUEST)
//      contentAsString(signup) must contain(Messages("portal.signup.badTimestamp"))
//
//      val badData2 = data
//        .updated(SignupData.TIMESTAMP, Seq("bad-date"))
//      val signup2 = route(FakeRequest(POST, profileRoutes.signupPost().url)
//        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData2).get
//      status(signup2) must equalTo(BAD_REQUEST)
//      contentAsString(signup2) must contain(Messages("portal.signup.badTimestamp"))
//    }
//
//    "prevent signup with filled blank field" in new FakeApp {
//      val badData = data.updated(SignupData.BLANK_CHECK, Seq("iAmARobot"))
//      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
//        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
//      status(signup) must equalTo(BAD_REQUEST)
//      contentAsString(signup) must contain(Messages("portal.signup.badSignupInput"))
//    }
//
//    "prevent signup where terms are not agreed" in new FakeApp {
//      val badData = data.updated(SignupData.AGREE_TERMS, Seq(false.toString))
//      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
//        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), badData).get
//      status(signup) must equalTo(BAD_REQUEST)
//      contentAsString(signup) must contain(Messages("portal.signup.agreeTerms"))
//    }
//
//    "allow unverified user to log in" in new FakeApp {
//      val signup = route(FakeRequest(POST, profileRoutes.signupPost().url)
//        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), data).get
//      status(signup) must equalTo(SEE_OTHER)
//      mocks.userFixtures.find(_._2.email == testEmail) must beSome.which { case(uid, u) =>
//        // Ensure we can log in and view our profile
//        val index = route(fakeLoggedInHtmlRequest(u, GET,
//          profileRoutes.profile().url)).get
//        status(index) must equalTo(OK)
//        contentAsString(index) must contain(testName)
//      }
//    }
  }
}
