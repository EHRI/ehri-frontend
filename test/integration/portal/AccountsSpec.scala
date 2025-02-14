package integration.portal

import auth.HashedPassword
import auth.oauth2.providers.GoogleOAuth2Provider
import auth.sso.DiscourseSSO
import forms.HoneyPotForm._
import forms.TimeCheckForm._
import helpers.IntegrationTestRunner
import mockdata.{unprivilegedUser, unverifiedUser}
import models.{Account, SignupData}
import play.api.Application
import play.api.cache.SyncCacheApi
import play.api.i18n.MessagesApi
import play.api.test.{FakeRequest, Injecting, WithApplication}

import java.util.UUID


class AccountsSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  private val accountRoutes = controllers.portal.account.routes.Accounts

  private implicit def cache(implicit app: Application): SyncCacheApi = app.injector.instanceOf[SyncCacheApi]

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

    "allow users to change their email address" in new ITestApp {
      await(mockAccounts.update(privilegedUser
        .copy(password = Some(HashedPassword.fromPlain("letmein")))))
      val data: Map[String, Seq[String]] = Map(
        SignupData.EMAIL -> Seq("new@example.com"),
        SignupData.PASSWORD -> Seq("letmein"))
      val r = FakeRequest(accountRoutes.changeEmailPost())
        .withUser(privilegedUser)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(r) must equalTo(SEE_OTHER)
      redirectLocation(r) must beSome(
        controllers.portal.users.routes.UserProfiles.updateProfile().url)

      await(mockAccounts.get(privilegedUser.id)).email must_== "new@example.com"
    }

    "disallow changing email address to one that already exists" in new ITestApp {
      await(mockAccounts.update(privilegedUser
        .copy(password = Some(HashedPassword.fromPlain("letmein")))))
      val data: Map[String, Seq[String]] = Map(
        SignupData.EMAIL -> Seq("example2@example.com"),
        SignupData.PASSWORD -> Seq("letmein"))
      val r = FakeRequest(accountRoutes.changeEmailPost())
        .withUser(privilegedUser)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(r) must equalTo(BAD_REQUEST)
   }

    "allow users to change their passwords" in new ITestApp {
      await(mockAccounts.update(privilegedUser
        .copy(password = Some(HashedPassword.fromPlain("letmein")))))
      val data: Map[String, Seq[String]] = Map(
        "current" -> Seq("letmein"),
        SignupData.PASSWORD -> Seq("newp4sswd"),
        SignupData.CONFIRM -> Seq("newp4sswd")
      )
      val r = FakeRequest(accountRoutes.changePasswordPost())
        .withUser(privilegedUser)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(r) must equalTo(SEE_OTHER)
      redirectLocation(r) must beSome(
        controllers.portal.users.routes.UserProfiles.updateProfile().url)

      await(mockAccounts.authenticateById(privilegedUser.id, "newp4sswd")) must beSome
    }

    "allow users to confirm their email addresses via form" in new ITestApp {
      val uuid = UUID.randomUUID()
      await(mockAccounts.createToken(unverifiedUser.id, uuid, isSignUp = true))
      val r = FakeRequest(accountRoutes.confirmEmail(uuid.toString))
        .call()
      status(r) must equalTo(OK)
    }

    "allow users to confirm their email addresses via form POST" in new ITestApp {
      val uuid = UUID.randomUUID()
      await(mockAccounts.createToken(unverifiedUser.id, uuid, isSignUp = true))
      val r = FakeRequest(accountRoutes.confirmEmailPost())
        .withFormUrlEncodedBody("token" -> uuid.toString)
        .call()
      status(r) must equalTo(SEE_OTHER)
      await(mockAccounts.findById(mockdata.unprivilegedUser.id)) must beSome.which { check: Account =>
        check.verified must beTrue
      }
    }

    "redirect already-validated users" in new ITestApp {
      val uuid = UUID.randomUUID()
      await(mockAccounts.createToken(privilegedUser.id, uuid, isSignUp = true))
      val r = FakeRequest(accountRoutes.confirmEmail(uuid.toString))
        .withUser(privilegedUser)
        .call()
      status(r) must equalTo(SEE_OTHER)
      flash(r).get("success") must beSome("signup.validation.alreadyValidated")
    }

    "show an error with an invalid token" in new ITestApp {
      val r = FakeRequest(accountRoutes.confirmEmail("bjsaljlj3ijvkshskjhnkvn kj"))
        .call()
      status(r) must equalTo(BAD_REQUEST)
    }

    "show an error with an invalid token via POST" in new ITestApp {
      val r = FakeRequest(accountRoutes.confirmEmailPost())
        .withFormUrlEncodedBody("token" -> "bjsaljlj3ijvkshskjhnkvn kj")
        .call()
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must contain(message("signup.validation.badToken"))
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
      private implicit val messagesApi: play.api.i18n.MessagesApi = inject[MessagesApi]
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

  "ORCID OAuth2" should {
    "allow connecting a user's ORCID account" in new ITestApp {
      val singleUseKey = "useOnce2"
      val randomState = "40fj09uoij0u43lnf"
      cache.set(singleUseKey, randomState)
      val connectORCID = FakeRequest(accountRoutes.connectORCIDPost(code = Some("blah"), state = Some(randomState)))
        .withSession("sid" -> singleUseKey)
        .withUser(privilegedUser)
        .call()
      status(connectORCID) must equalTo(SEE_OTHER)
      flash(connectORCID).get("success") must beSome.which { fl =>
        fl must contain("profile.orcid.connected")
      }
      cache.get[String](singleUseKey) must beNone
    }
  }

  "Discourse SSO" should {

    val CLIENT = "forums"
    val KEY = "DISCOURSE_TEST_SECRET"
    val URL = "https://discuss.ehri-project.eu"

    "deny access to unverified users" in new ITestApp {
      await(mockAccounts.update(unprivilegedUser.copy(verified = false)))
      val ssoLogin = FakeRequest(accountRoutes.sso(CLIENT, "foo", "bar"))
        .withUser(unprivilegedUser)
        .call()
      status(ssoLogin) must_== UNAUTHORIZED
    }

    "give a bad request when SSO not configured" in new ITestApp {
      val ssoLogin = FakeRequest(accountRoutes.sso(CLIENT, "foo", "bar"))
        .withUser(unprivilegedUser)
        .call()
      status(ssoLogin) must_== BAD_REQUEST
    }

    "correctly parse data" in new ITestApp(specificConfig = Map(
      s"sso.$CLIENT.discourse_connect_secret" -> KEY,
      s"sso.$CLIENT.discourse_endpoint" -> URL
    )) {

      val helper = DiscourseSSO(URL, KEY)

      val nonce = UUID.randomUUID().toString
      val (payload, sig) = helper.encode(Seq("nonce" -> nonce))

      val ssoLogin = FakeRequest(accountRoutes.sso(CLIENT, payload, sig))
        .withUser(privilegedUser)
        .call()
      status(ssoLogin) must_== SEE_OTHER
      val loc = redirectLocation(ssoLogin)
      loc must beSome.which { (s:String) =>
        s must startWith(URL)

        val qs = s.substring(s.lastIndexOf('?') + 1)
        val data: Map[String, String] = utils.http.parseQueryString(qs).toMap

        val sso = data.get("sso")
        sso must beSome
        val newSig = data.get("sig")
        newSig must beSome

        val outPayload = helper.decode(sso.get, newSig.get)
        outPayload must_== Seq(
          "nonce" -> nonce,
          "external_id" -> privilegedUser.id,
          "email" -> privilegedUser.email,
          "name" -> "Mike",
          "admin" -> "true",
          "moderator" -> "false"
        )
      }
    }
  }
}
