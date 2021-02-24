package integration.admin

import helpers.{TestConfiguration, UserFixtures}
import models.{Account, SignupData}
import play.api.test._



class ApplicationSpec extends PlaySpecification with TestConfiguration with UserFixtures {
  sequential

  private val accountRoutes = controllers.portal.account.routes.Accounts
  private val portalRoutes = controllers.portal.routes.Portal

  override def getConfig = Map("ehri.secured" -> true)

  "Application" should {
    "send 404 on a bad request" in new ITestApp {
      val r404 = FakeRequest(GET, "/NOTHINGHERE").call()
      status(r404) must equalTo(NOT_FOUND)
    }

    "show readonly warning when in READONLY mode" in new ITestApp {
      // NB: The location of this file is set in the application.conf
      // and overridden in production configs by prod.conf. The default
      // one is a relative file called "READONLY". We can therefore
      // create this in the CWD to test.
      val f = new java.io.File("READONLY")
      f.createNewFile()
      // Since we're not running the DB for this test we just
      // try a route that doesn't need the dataApi - there's
      // nothing significant about using the /forgot endpoint
      // here, only that it's a simple layout
      try {
        val pageReadOnly = FakeRequest(accountRoutes.forgotPassword()).call()
        status(pageReadOnly) must equalTo(OK)
        contentAsString(pageReadOnly) must contain(message("errors.readonly"))

        // Deleting the file should make the message go away
        f.delete()

        val page = FakeRequest(accountRoutes.forgotPassword()).call()
        status(page) must equalTo(OK)
        contentAsString(page) must not contain message("errors.readonly")
      } finally {
        f.deleteOnExit()
      }
    }

    "show a 503 in maintenance mode" in new ITestApp {
      val f = new java.io.File("MAINTENANCE")
      f.createNewFile()
      try {
        val pageOffline = FakeRequest(portalRoutes.dataPolicy()).call()
        status(pageOffline) must equalTo(SERVICE_UNAVAILABLE)
        contentAsString(pageOffline) must contain(message("errors.maintenance"))

        // Deleting the file should make the message go away
        f.delete()

        val pageOnline = FakeRequest(portalRoutes.dataPolicy()).call()
        status(pageOnline) must equalTo(OK)
        contentAsString(pageOnline) must not contain message("errors.maintenance")
      } finally {
        f.deleteOnExit()
      }
    }

    "show a global message if the message file is present" in new ITestApp {
      import org.apache.commons.io.FileUtils
      val f = new java.io.File("MESSAGE")
      f.createNewFile()
      val veryImportantMessage = "This is a very important message!"
      FileUtils.write(f, veryImportantMessage, "UTF-8")
      try {
        val pageWithMessage = FakeRequest(portalRoutes.dataPolicy()).call()
        status(pageWithMessage) must equalTo(OK)
        contentAsString(pageWithMessage) must contain(veryImportantMessage)

        // Deleting the file should make the message go away
        f.delete()

        val pageWithoutMessage = FakeRequest(portalRoutes.dataPolicy()).call()
        status(pageWithoutMessage) must equalTo(OK)
        contentAsString(pageWithoutMessage) must not contain veryImportantMessage
      } finally {
        f.deleteOnExit()
      }
    }

    "handle IP filtering for maintenance" in new ITestApp {
      import org.apache.commons.io.FileUtils
      val f = new java.io.File("IP_WHITELIST")
      f.createNewFile()
      val req = FakeRequest(portalRoutes.dataPolicy())
      FileUtils.write(f, req.remoteAddress, "UTF-8")
      try {
        val pageWithMessage = req.call()
        status(pageWithMessage) must equalTo(OK)
        contentAsString(pageWithMessage) must contain(req.remoteAddress)

        // Deleting the file should make the message go away
        f.delete()

        val pageWithoutMessage = FakeRequest(portalRoutes.dataPolicy()).call()
        status(pageWithoutMessage) must equalTo(OK)
        contentAsString(pageWithoutMessage) must not contain req.remoteAddress
      } finally {
        f.deleteOnExit()
      }
    }

    "disallow the right stuff in robots.txt" in new ITestApp {
      val robots = contentAsString(FakeRequest(GET, "/robots.txt").call())
      robots must contain("Disallow: " + portalRoutes.personalisedActivity().url)
      robots must contain("Disallow: " + accountRoutes.login().url)
      robots must contain("Disallow: " + controllers.portal.routes.Feedback.feedback().url)
      robots must contain("Disallow: " + controllers.portal.social.routes.Social.browseUsers().url)
      robots must contain("Disallow: " + controllers.portal.annotate.routes.Annotations.searchAll().url.replaceFirst("/$", ""))
      robots must contain("Disallow: " + controllers.admin.routes.Home.index())
    }

    "redirect 301 for trailing-slash URLs" in new ITestApp {
      val home = FakeRequest(GET, controllers.admin.routes.Home.overview().url + "/")
        .withUser(mockdata.publicUser)
        .call()
      status(home) must equalTo(MOVED_PERMANENTLY)
    }

    "deny non-staff users access to admin areas" in new ITestApp {
      val home = FakeRequest(GET, controllers.admin.routes.Home.index().url)
        .withUser(mockdata.publicUser)
        .call()
      status(home) must equalTo(UNAUTHORIZED)
    }

    "deny non-verified users access to admin areas" in new ITestApp {
      var user = Account("pete", "unverified@example.com", verified = false, staff = true)
      mockdata.accountFixtures += user.id -> user

      val home = FakeRequest(controllers.admin.routes.Home.index())
        .withUser(user)
        .call()
      status(home) must equalTo(UNAUTHORIZED)
    }

    "redirect to default URL when accessing login page when logged in" in new ITestApp {
      val login = FakeRequest(accountRoutes.login())
        .withUser(mockdata.publicUser)
        .call()
      println(contentAsString(login))
      status(login) must equalTo(SEE_OTHER)
    }

    "not redirect to login at admin home when unsecured" in new ITestApp(Map("ehri.secured" -> false)) {
      val home = FakeRequest(controllers.admin.routes.Home.index()).call()
      status(home) must equalTo(OK)
    }

    "allow access to the openid callback url, and return a bad request" in new ITestApp {
      val home = FakeRequest(accountRoutes.openIDCallback())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .call()
      status(home) must equalTo(BAD_REQUEST)
    }
    
    "give a capture error submitting a forgot password form" in new ITestApp(Map("recaptcha.skip" -> false)) {
      val data: Map[String,Seq[String]] = Map(
        "email" -> Seq("test@example.com"),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val forgot = FakeRequest(accountRoutes.forgotPasswordPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(forgot) must equalTo(BAD_REQUEST)
      contentAsString(forgot) must contain(message("error.badRecaptcha"))
    }

    "don't give error submitting a forgot password form with the right capcha but the wrong email" in new ITestApp(Map("recaptcha.skip" -> true)) {
      val data: Map[String,Seq[String]] = Map(
        "email" -> Seq("test@example.com"),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val forgot = FakeRequest(accountRoutes.forgotPasswordPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(forgot) must equalTo(SEE_OTHER)
      flash(forgot).apply("warning") must_== "login.password.reset.sentLink"
    }

    "create a reset token on password reset with correct email" in new ITestApp(Map("recaptcha.skip" -> true)) {
      val numSentMails = mailBuffer.size
      val data: Map[String,Seq[String]] = Map(
        "email" -> Seq(mockdata.unprivilegedUser.email),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val forgot = FakeRequest(accountRoutes.forgotPasswordPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(forgot) must equalTo(SEE_OTHER)
      mailBuffer.size must beEqualTo(numSentMails + 1)
      mailBuffer.last.to must contain(mockdata.unprivilegedUser.email)
    }

    "check password reset token works (but only once)" in new ITestApp(Map("recaptcha.skip" -> true)) {
      val numSentMails = mailBuffer.size
      val data: Map[String,Seq[String]] = Map(
        "email" -> Seq(mockdata.unprivilegedUser.email),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val forgot = FakeRequest(accountRoutes.forgotPasswordPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(forgot) must equalTo(SEE_OTHER)
      mailBuffer.size must beEqualTo(numSentMails + 1)
      mailBuffer.last.to must contain(mockdata.unprivilegedUser.email)

      val token = mockdata.tokenFixtures.last._1
      val resetForm = FakeRequest(accountRoutes.resetPassword(token))
        .callWith(data)
      status(resetForm) must equalTo(OK)

      val rstData: Map[String,Seq[String]] = Map(
        "password" -> Seq("hellokitty"),
        "confirm"  -> Seq("hellokitty"),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )
      val resetPost = FakeRequest(accountRoutes.resetPasswordPost(token))
        .withCsrf
        .callWith(rstData)
      status(resetPost) must equalTo(SEE_OTHER)

      val expired = FakeRequest(accountRoutes.resetPassword(token))
        .call()
      status(expired) must equalTo(SEE_OTHER)
      flash(expired).get("danger") must beSome.which{ msg =>
        msg must equalTo("login.error.badResetToken")
      }
    }



    "rate limit repeated requests with a timeout" in new ITestApp(
        Map("ehri.ratelimit.limit" -> 2, "ehri.ratelimit.timeout" -> "1 second")) {
      val data = Map(
        SignupData.EMAIL -> Seq("test@nothing.com"),
        SignupData.PASSWORD -> Seq("blah"),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )

      val attempt1 = FakeRequest(accountRoutes.passwordLoginPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(attempt1) must equalTo(BAD_REQUEST)
      val attempt2 = FakeRequest(accountRoutes.passwordLoginPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(attempt2) must equalTo(BAD_REQUEST)
      val attempt3 = FakeRequest(accountRoutes.passwordLoginPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(attempt3) must equalTo(TOO_MANY_REQUESTS)

      // Wait for the timeout to expire and try again...
      Thread.sleep(1500)
      val attempt4 = FakeRequest(accountRoutes.passwordLoginPost())
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
        .callWith(data)
      status(attempt4) must equalTo(BAD_REQUEST)
    }
  }
}
