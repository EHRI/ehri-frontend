package integration.portal

import auth.HashedPassword
import auth.handler.SessionToken
import helpers.IntegrationTestRunner
import models.SignupData
import play.api.Application
import play.api.mvc.Result
import play.api.test.FakeRequest

import scala.concurrent.Future
import scala.concurrent.duration._


class SessionsSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  private val profileRoutes = controllers.portal.users.routes.UserProfiles
  private val accountRoutes = controllers.portal.account.routes.Accounts

  private def now: Long = System.currentTimeMillis() / 1000

  private def token(version: Int = 0, issuedAt: Long = now, lastSeen: Long = now): String =
    SessionToken(privilegedUser.id, version, issuedAt, lastSeen).encode

  private def profileWith(token: String)(implicit app: Application): Future[Result] =
    FakeRequest(profileRoutes.updateProfile()).withHeaders(AUTH_TEST_HEADER_NAME -> token).call()

  private def loggedIn(r: Future[Result]) = status(r) must equalTo(OK)

  private def loggedOut(r: Future[Result]) = redirectLocation(r) must beSome(accountRoutes.login().url)

  "Login sessions" should {
    "be accepted when current" in new ITestApp {
      loggedIn(profileWith(token()))
    }

    "be rejected once the account's sessions have ended" in new ITestApp {
      val old = token()
      await(mockAccounts.endSessions(privilegedUser.id))
      loggedOut(profileWith(old))
      loggedIn(profileWith(token(version = 1)))
    }

    "be rejected after the maximum age" in new ITestApp {
      loggedOut(profileWith(token(issuedAt = now - 91.days.toSeconds)))
    }

    "be rejected after the idle timeout" in new ITestApp {
      loggedOut(profileWith(token(lastSeen = now - 8.days.toSeconds)))
    }

    "be rejected in the old format" in new ITestApp {
      loggedOut(profileWith(privilegedUser.id))
    }

    "allow logging in with an expired session cookie" in new ITestApp {
      await(mockAccounts.update(privilegedUser.copy(password = Some(HashedPassword.fromPlain("letmein")))))
      val r = FakeRequest(accountRoutes.passwordLoginPost())
        .withHeaders(AUTH_TEST_HEADER_NAME -> token(issuedAt = now - 91.days.toSeconds))
        .withCsrf
        .callWith(Map(SignupData.EMAIL -> Seq(privilegedUser.email), SignupData.PASSWORD -> Seq("letmein")))
      status(r) must equalTo(SEE_OTHER)
      val cookieName = app.configuration.get[String]("auth.session.cookieName")
      cookies(r).get(cookieName) must beSome.which { cookie =>
        loggedIn(FakeRequest(profileRoutes.updateProfile()).withCookies(cookie).call())
      }
    }

    "end other sessions, but not the current one, when the password changes" in new ITestApp {
      await(mockAccounts.update(privilegedUser.copy(password = Some(HashedPassword.fromPlain("letmein")))))
      val old = token()
      val r = FakeRequest(accountRoutes.changePasswordPost())
        .withHeaders(AUTH_TEST_HEADER_NAME -> old)
        .withCsrf
        .callWith(Map(
          "current" -> Seq("letmein"),
          SignupData.PASSWORD -> Seq("newp4sswd"),
          SignupData.CONFIRM -> Seq("newp4sswd")))
      status(r) must equalTo(SEE_OTHER)
      loggedOut(profileWith(old))

      val cookieName = app.configuration.get[String]("auth.session.cookieName")
      cookies(r).get(cookieName) must beSome.which { cookie =>
        loggedIn(FakeRequest(profileRoutes.updateProfile()).withCookies(cookie).call())
      }
    }
  }

  "Session tokens" should {
    "round trip" in {
      val t = SessionToken("user:with:colons", 2, 100L, 200L)
      SessionToken.parse(t.encode) must beSome(t)
    }

    "reject malformed values" in {
      SessionToken.parse("mike") must beNone
      SessionToken.parse("mike:x:1:2") must beNone
      SessionToken.parse(":1:2:3") must beNone
    }
  }
}
