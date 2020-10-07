package integration.portal

import auth.HashedPassword
import auth.oauth2.providers.GoogleOAuth2Provider
import helpers.IntegrationTestRunner
import models.SignupData
import play.api.i18n.MessagesApi
import play.api.cache.SyncCacheApi
import play.api.test.{FakeRequest, Injecting, WithApplication}
import forms.HoneyPotForm._
import forms.TimeCheckForm._


class AccountsSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  private val accountRoutes = controllers.portal.account.routes.Accounts

  private implicit def cache(implicit app: play.api.Application) = app.injector.instanceOf[SyncCacheApi]

  "Account views" should {
    "redirect to index page on log out" in new ITestApp {
      val logout = FakeRequest(accountRoutes.logout()).withUser(privilegedUser).call()
      status(logout) must equalTo(SEE_OTHER)
      flash(logout).get("success") must beSome.which { fl =>
        // NB: No i18n here...
        fl must contain("logout.confirmation")
      }
    }

    "allow user to login with password" in new ITestApp(
      specificConfig = Map(
        "recaptcha.skip" -> true,
        "ehri.signup.timeCheckSeconds" -> -1
      )
    ) {
      val data: Map[String, Seq[String]] = Map(
        // NB: Using toUpperCase here to test case insentitivity
        // on email login (bug #816)
        SignupData.EMAIL -> Seq(privilegedUser.email.toUpperCase),
        SignupData.PASSWORD -> Seq(testPassword),
        TIMESTAMP -> Seq(java.time.ZonedDateTime.now.toString),
        BLANK_CHECK -> Seq(""),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )

      await(mockAccounts.update(privilegedUser
        .copy(password = Some(HashedPassword.fromPlain(testPassword)))))
      val login = FakeRequest(accountRoutes.passwordLoginPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString).callWith(data)
      status(login) must equalTo(SEE_OTHER)
    }
  }

  "OAuth2" should {
    "allow user to log in" in new ITestApp {
      // Using the existing Google OAuth2 association
      // in the mock database. The key here is that
      // the data in the `googleUserData.txt` resource
      // contains the same email (example1@example.com) as
      // a user in the mocks DB (mike)
      val singleUseKey = "useOnce"
      val randomState = "473284374"
      cache.set(singleUseKey, randomState)
      val login = FakeRequest(accountRoutes.oauth2Login(GoogleOAuth2Provider(app.configuration).name,
        code = Some("blah"), state = Some(randomState)))
        .withSession("sid" -> singleUseKey).call()
      status(login) must equalTo(SEE_OTHER)
      // The handle should have deleted the single-use key
      cache.get[String](singleUseKey) must beNone
    }

    "error with bad session state" in new WithApplication with Injecting {
      private implicit val messagesApi = inject[MessagesApi]
      val singleUseKey = "useOnce"
      cache.set(singleUseKey, "jdjjjr")
      val login = FakeRequest(
        accountRoutes.oauth2Login(GoogleOAuth2Provider(app.configuration).name,
          code = Some("blah"), state = Some("dk3kdm34")))
        .withSession("sid" -> singleUseKey).call()
      status(login) must equalTo(SEE_OTHER)
      flash(login).get("danger") must beSome(message("login.error.oauth2.badSessionId", "Google"))
    }

    "error with bad provider" in new ITestApp {
      val login = FakeRequest(accountRoutes.oauth2Register("no-provider")).call()
      status(login) must equalTo(NOT_FOUND)
    }
  }
}
