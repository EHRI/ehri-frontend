package integration.portal

import auth.HashedPassword
import auth.oauth2.providers.{GoogleOAuth2Provider, MicrosoftOAuth2Provider}
import auth.sso.DiscourseSSO
import forms.HoneyPotForm._
import forms.TimeCheckForm._
import helpers.IntegrationTestRunner
import mockdata.{unprivilegedUser, unverifiedUser}
import models.{Account, SignupData}
import play.api.Application
import play.api.cache.SyncCacheApi
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Injecting, WithApplication}

import java.util.UUID


class AccountsSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  private val accountRoutes = controllers.portal.account.routes.Accounts

  private implicit def cache(implicit app: Application): SyncCacheApi = app.injector.instanceOf[SyncCacheApi]

  private def changeEmail(email: String)(implicit app: Application) = {
    await(mockAccounts.update(privilegedUser.copy(password = Some(HashedPassword.fromPlain("letmein")))))
    FakeRequest(accountRoutes.changeEmailPost())
      .withUser(privilegedUser)
      .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
      .callWith(Map(SignupData.EMAIL -> Seq(email), SignupData.PASSWORD -> Seq("letmein")))
  }

  "Account views" should {
    "redirect to index page on log out" in new ITestApp {
      val logout = FakeRequest(accountRoutes.logout()).withUser(privilegedUser).call()
      status(logout) must equalTo(SEE_OTHER)
      flash(logout).get("success") must beSome.which { fl =>
        // NB: No i18n here...
        fl must contain("logout.confirmation")
      }
    }

    "ensure access to the login page works when public view is disabled" in new ITestApp(
      specificConfig = Map("ehri.portal.secured" -> true)) {
      val login = FakeRequest(accountRoutes.login()).call()
      status(login) must_== OK
    }

    "ensure user can login when public view is disabled" in new ITestApp(
        specificConfig = Map("ehri.portal.secured" -> true)) {
      val data: Map[String, Seq[String]] = Map(
        SignupData.EMAIL -> Seq(privilegedUser.email),
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
      redirectLocation(login) must beSome(controllers.portal.users.routes.UserProfiles.profile().url)
    }

    "ensure user can reset password when public view is disabled" in new ITestApp(
        specificConfig = Map("ehri.portal.secured" -> true, "recaptcha.skip" -> true)) {
      val forgotForm = FakeRequest(accountRoutes.forgotPassword()).call()
      status(forgotForm) must_== OK

      val data: Map[String, Seq[String]] = Map(
        "email" -> Seq(privilegedUser.email),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val forgot = FakeRequest(accountRoutes.forgotPasswordPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString).callWith(data)
      status(forgot) must equalTo(SEE_OTHER)
      redirectLocation(forgot) must not(beSome(accountRoutes.login().url))

      val uuid = UUID.randomUUID()
      await(mockAccounts.createToken(privilegedUser.id, uuid, isSignUp = false))
      val resetForm = FakeRequest(accountRoutes.resetPassword(uuid.toString)).call()
      status(resetForm) must_== OK

      val rstData: Map[String, Seq[String]] = Map(
        "password" -> Seq("hellokitty"),
        "confirm" -> Seq("hellokitty"),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val resetPost = FakeRequest(accountRoutes.resetPasswordPost(uuid.toString))
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString).callWith(rstData)
      status(resetPost) must equalTo(SEE_OTHER)
      redirectLocation(resetPost) must not(beSome(accountRoutes.login().url))
    }

    "claim an unverified account when resetting its password" in new ITestApp {
      mockdata.oauth2AssociationFixtures += models.OAuth2Association(unverifiedUser.id, "55555", "microsoft", None)
      val uuid = UUID.randomUUID()
      await(mockAccounts.createToken(unverifiedUser.id, uuid, isSignUp = false))
      val rstData: Map[String, Seq[String]] = Map(
        "password" -> Seq("hellokitty"),
        "confirm" -> Seq("hellokitty"),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val resetPost = FakeRequest(accountRoutes.resetPasswordPost(uuid.toString))
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString).callWith(rstData)
      status(resetPost) must equalTo(SEE_OTHER)
      await(mockAccounts.findById(unverifiedUser.id)) must beSome.which { account =>
        account.verified must beTrue
        account.password.exists(_.check("hellokitty")) must beTrue
      }
      mockdata.oauth2AssociationFixtures.exists(_.id == unverifiedUser.id) must beFalse
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
      val signupToken = UUID.randomUUID().toString
      val resetToken = UUID.randomUUID().toString
      await(mockAccounts.createToken(privilegedUser.id, UUID.fromString(signupToken), isSignUp = true))
      await(mockAccounts.createToken(privilegedUser.id, UUID.fromString(resetToken), isSignUp = false))
      val r = changeEmail("new@example.com")
      status(r) must equalTo(SEE_OTHER)
      redirectLocation(r) must beSome(
        controllers.portal.users.routes.UserProfiles.updateProfile().url)

      val account = await(mockAccounts.get(privilegedUser.id))
      account.email must_== "new@example.com"
      account.verified must beFalse
      mailBuffer.last.to must contain("new@example.com")
      await(mockAccounts.findByToken(signupToken, isSignUp = true)) must beNone
      await(mockAccounts.findByToken(resetToken)) must beNone
    }

    "change email address even if the validation email can't be sent" in new ITestApp {
      val newEmail = "new@" + utils.MockBufferedMailer.failureDomain
      status(changeEmail(newEmail)) must equalTo(SEE_OTHER)
      await(mockAccounts.get(privilegedUser.id)).email must_== newEmail
    }

    "disallow changing email address to one that already exists" in new ITestApp {
      status(changeEmail("example2@example.com")) must equalTo(BAD_REQUEST)
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

  private def oauth2Login(provider: String)(implicit app: Application) = {
    val singleUseKey = UUID.randomUUID().toString
    val randomState = UUID.randomUUID().toString
    cache.set(singleUseKey, randomState)
    FakeRequest(accountRoutes.oauth2Login(provider, code = Some("blah"), state = Some(randomState)))
      .withSession("sid" -> singleUseKey).call()
  }

  private def microsoftLogin(id: String, email: String) = Map(
    "test.oauth2.microsoft.userData" -> Json.stringify(Json.obj("sub" -> id, "email" -> email, "name" -> "New User")))

  private val verifiedGoogleLogin = Map(
    "test.oauth2.google.userData" -> Json.stringify(Json.obj("id" -> "987654321",
      "email" -> mockdata.privilegedUser.email, "verified_email" -> true, "name" -> "Any Name")))

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
      session(login).get("sid") must beNone
    }

    "link a verified email to an existing account" in new ITestApp(verifiedGoogleLogin) {
      val login = oauth2Login("google")
      status(login) must equalTo(SEE_OTHER)
      flash(login).get("danger") must beNone
      mockdata.oauth2AssociationFixtures.exists(a =>
        a.id == mockdata.privilegedUser.id && a.provider == "google" && a.providerId == "987654321") must beTrue
    }

    "remove other logins when linking a verified email to an unverified account" in new ITestApp(verifiedGoogleLogin) {
      mockdata.accountFixtures += privilegedUser.id -> privilegedUser.copy(verified = false,
        password = Some(HashedPassword.fromPlain("other-persons-password")))
      mockdata.oauth2AssociationFixtures += models.OAuth2Association(privilegedUser.id, "55555", "microsoft", None)
      val login = oauth2Login("google")
      status(login) must equalTo(SEE_OTHER)
      flash(login).get("danger") must beNone
      await(mockAccounts.findById(privilegedUser.id)) must beSome.which { account =>
        account.verified must beTrue
        account.password must beNone
      }
      mockdata.oauth2AssociationFixtures.filter(_.id == privilegedUser.id).map(_.provider) must equalTo(Seq("google"))
    }

    "not link an unverified email to an existing account" in new ITestApp {
      // The Microsoft test data has the same email as an existing account
      val login = oauth2Login(MicrosoftOAuth2Provider(app.configuration).name)
      status(login) must equalTo(SEE_OTHER)
      redirectLocation(login) must beSome(accountRoutes.login().url)
      flash(login).get("danger") must beSome(message("login.error.oauth2.unverifiedEmail"))
      mockdata.oauth2AssociationFixtures.exists(a =>
        a.provider == "microsoft" && a.providerId == "123456789") must beFalse
    }

    "create an unverified account for an unverified new email" in new ITestApp(
        microsoftLogin("55555", "new-microsoft-user@example.com")) {
      val numSentMails = mailBuffer.size
      val login = oauth2Login("microsoft")
      status(login) must equalTo(SEE_OTHER)
      flash(login).get("success") must beSome("signup.confirmation")
      await(mockAccounts.findByEmail("new-microsoft-user@example.com")) must beSome.which { account =>
        account.verified must beFalse
      }
      mailBuffer.size must equalTo(numSentMails + 1)
      mailBuffer.last.to must contain("new-microsoft-user@example.com")
    }

    "log in a new unverified account even if the validation email can't be sent" in new ITestApp(
        microsoftLogin("66666", "new-user@" + utils.MockBufferedMailer.failureDomain)) {
      val login = oauth2Login("microsoft")
      status(login) must equalTo(SEE_OTHER)
      flash(login).get("danger") must beNone
      mockdata.oauth2AssociationFixtures.exists(a =>
        a.provider == "microsoft" && a.providerId == "66666") must beTrue
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
