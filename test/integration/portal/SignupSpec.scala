package integration.portal

import java.time.ZonedDateTime

import helpers.IntegrationTestRunner
import models._
import play.api.test.FakeRequest


class SignupSpec extends IntegrationTestRunner {

  import utils.forms.HoneyPotForm._
  import utils.forms.TimeCheckForm._
  private val accountRoutes = controllers.portal.account.routes.Accounts

  val COOKIE_NAME: String = "PLAY2AUTH_SESS_ID"

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
      SignupData.PASSWORD -> Seq(testPassword),
      SignupData.CONFIRM -> Seq(testPassword),
      TIMESTAMP -> Seq(ZonedDateTime.now.toString),
      BLANK_CHECK -> Seq(""),
      SignupData.AGREE_TERMS -> Seq(true.toString),
      CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
    )

    "create a validation token and send a mail on signup" in new ITestApp {
      val numSentMails = mailBuffer.size
      val numAccounts = mockdata.accountFixtures.size
      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(data)
      status(signup) must equalTo(SEE_OTHER)
      mailBuffer.size must beEqualTo(numSentMails + 1)
      mailBuffer.last.to must contain(testEmail)
      mockdata.accountFixtures.size must equalTo(numAccounts + 1)
      val userOpt = mockdata.accountFixtures.values.find(u => u.email == testEmail)
      userOpt must beSome.which { user =>
        user.verified must beFalse
      }
    }

    "prevent signup with too short a password" in new ITestApp {
      val length = app.configuration.get[Int]("ehri.passwords.minLength")
      val badData = data
        .updated(SignupData.PASSWORD, Seq("short"))
        .updated(SignupData.CONFIRM, Seq("short"))
      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(badData)
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(messagesApi("error.minLength", length))
    }

    "prevent signup with mismatched passwords" in new ITestApp {
      val badData = data
        .updated(SignupData.CONFIRM, Seq("blibblob"))
      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(badData)
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(messagesApi("signup.badPasswords"))
    }

    "prevent signup with invalid time diff" in new ITestApp(specificConfig = Map("ehri.signup.timeCheckSeconds" -> 5)) {
      val badData = data
        .updated(TIMESTAMP, Seq(java.time.ZonedDateTime.now.toString))
      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(badData)
      println(redirectLocation(signup))
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(messagesApi("constraints.timeCheckSeconds.failed"))

      val badData2 = data
        .updated(TIMESTAMP, Seq("bad-date"))
      val signup2 = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(badData2)
      status(signup2) must equalTo(BAD_REQUEST)
      contentAsString(signup2) must contain(messagesApi("constraints.timeCheckSeconds.failed"))
    }

    "prevent signup with filled blank field" in new ITestApp {
      val badData = data.updated(BLANK_CHECK, Seq("iAmARobot"))
      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(badData)
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(messagesApi("constraints.honeypot.failed"))
    }

    "prevent signup where terms are not agreed" in new ITestApp {
      val badData = data.updated(SignupData.AGREE_TERMS, Seq(false.toString))
      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(badData)
      status(signup) must equalTo(BAD_REQUEST)
      contentAsString(signup) must contain(messagesApi("signup.agreeTerms"))
    }

    "allow unverified user to log in" in new ITestApp {
      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(data)
      status(signup) must equalTo(SEE_OTHER)
      mockdata.accountFixtures.find(_._2.email == testEmail) must beSome.which { case(uid, u) =>
        // Ensure we can log in and view our profile
        val index = FakeRequest(controllers.portal.users.routes.UserProfiles.profile())
          .withUser(u).call()
        status(index) must equalTo(OK)
        contentAsString(index) must contain(testName)
      }
    }

    "allow log in after sign up" in new ITestApp {
      val testEmail2 = "newuser@example.com"
      val data2 = data.updated(SignupData.EMAIL, Seq(testEmail2))

      val signup = FakeRequest(accountRoutes.signupPost()).withCsrf.callWith(data2)
      status(signup) must equalTo(SEE_OTHER)
      mockdata.accountFixtures.find(_._2.email == testEmail2) must beSome.which { case (uid, u) =>
        u.hasPassword must beTrue

        val logout = FakeRequest(accountRoutes.logout()).withUser(u).call()
        status(logout) must equalTo(SEE_OTHER)

        implicit val dateTimeOrdering: Ordering[ZonedDateTime] = Ordering.fromLessThan(_ isBefore _)
        val time = ZonedDateTime.now()
        val login = FakeRequest(accountRoutes.passwordLoginPost()).withCsrf.callWith(data2)
        status(login) must equalTo(SEE_OTHER)
        redirectLocation(login) must beSome.which { loc =>
          loc must equalTo(controllers.portal.users.routes.UserProfiles.profile().url)
          // Check login time has been updated...
          mockdata.accountFixtures.get(uid) must beSome.which { u2 =>
            u2.hasPassword must beTrue
            u2.lastLogin must beSome.which { t2 =>
              t2 must beGreaterThan(time)
            }
          }
        }
      }
    }
  }
}
