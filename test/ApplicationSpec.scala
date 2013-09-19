package test

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._

import helpers.TestMockLoginHelper
import play.api.i18n.Messages
import play.filters.csrf.CSRF
import mocks.MockBufferedMailer
import models.sql.MockAccountDAO

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends Specification with TestMockLoginHelper {
  sequential

  // Settings specific to this spec...
  override def getConfig = super.getConfig ++ Map[String,Any]("ehri.secured" -> true)

  "Application" should {
    "send 404 on a bad request" in {
      running(FakeApplication(withGlobal = Some(getGlobal))) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "render something at root url" in {
      running(FakeApplication(withGlobal = Some(getGlobal))) {
        val home = route(FakeRequest(GET, "/")).get
        status(home) must equalTo(OK)
      }
    }

    "deny non-staff users access to admin areas" in {
      running(FakeApplication(withGlobal = Some(getGlobal))) {
        val home = route(fakeLoggedInHtmlRequest(mocks.publicUser, GET,
          controllers.admin.routes.Home.index.url)).get
        status(home) must equalTo(UNAUTHORIZED)
        val login = route(fakeLoggedInHtmlRequest(mocks.publicUser, GET,
          controllers.core.routes.Admin.login.url)).get
        status(login) must equalTo(OK)
        val openid = route(fakeLoggedInHtmlRequest(mocks.publicUser, GET,
          controllers.core.routes.OpenIDLoginHandler.openIDLogin.url)).get
        status(openid) must equalTo(OK)
      }
    }

    "not redirect to login at admin home when unsecured" in {
      running(FakeApplication(withGlobal = Some(getGlobal),
        additionalConfiguration = Map("ehri.secured" -> false))) {
        val home = route(FakeRequest(GET,
          controllers.admin.routes.Home.index.url)).get
        status(home) must equalTo(OK)
      }
    }

    "allow access to the openid callback url, and redirect with flash error" in {
      running(FakeApplication(withGlobal = Some(getGlobal))) {
        val home = route(FakeRequest(GET,
          controllers.core.routes.OpenIDLoginHandler.openIDCallback.url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString)).get
        status(home) must equalTo(SEE_OTHER)
        val err = flash(home).get("error")
        err must beSome
        err.get must equalTo(Messages("openid.openIdError", null))
      }
    }
    
    "give a capture error submitting a forgot password form" in {
      running(FakeApplication(withGlobal = Some(getGlobal))) {
        val data: Map[String,Seq[String]] = Map(
          "email" -> Seq("test@example.com"),
          CSRF.Conf.TOKEN_NAME -> Seq(fakeCsrfString)
        )
        val forgot = route(FakeRequest(POST,
          controllers.core.routes.Admin.forgotPasswordPost().url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString), data).get
        status(forgot) must equalTo(BAD_REQUEST)
        contentAsString(forgot) must contain(Messages("error.badRecaptcha"))
      }
    }

    "give an error submitting a forgot password form with the right capcha but the wrong email" in {
      running(FakeApplication(withGlobal = Some(getGlobal),
          additionalConfiguration = Map("recaptcha.skip" -> true),
          additionalPlugins = getPlugins)) {
        val data: Map[String,Seq[String]] = Map(
          "email" -> Seq("test@example.com"),
          CSRF.Conf.TOKEN_NAME -> Seq(fakeCsrfString)
        )
        val forgot = route(FakeRequest(POST,
          controllers.core.routes.Admin.forgotPasswordPost().url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString), data).get
        status(forgot) must equalTo(BAD_REQUEST)
        contentAsString(forgot) must contain(Messages("error.emailNotFound"))
      }
    }

    "create a reset token on password reset with correct email" in {
      running(FakeApplication(withGlobal = Some(getGlobal),
        additionalConfiguration = Map("recaptcha.skip" -> true),
        additionalPlugins = getPlugins)) {
        val numSentMails = MockBufferedMailer.mailBuffer.size
        val data: Map[String,Seq[String]] = Map(
          "email" -> Seq(mocks.unprivilegedUser.email),
          CSRF.Conf.TOKEN_NAME -> Seq(fakeCsrfString)
        )
        val forgot = route(FakeRequest(POST,
          controllers.core.routes.Admin.forgotPasswordPost().url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString), data).get
        status(forgot) must equalTo(SEE_OTHER)
        MockBufferedMailer.mailBuffer.size must beEqualTo(numSentMails + 1)
        MockBufferedMailer.mailBuffer.last.to must contain(mocks.unprivilegedUser.email)
      }
    }

    "check password reset token works (but only once)" in {
      running(FakeApplication(withGlobal = Some(getGlobal),
        additionalConfiguration = Map("recaptcha.skip" -> true),
        additionalPlugins = getPlugins)) {
        val numSentMails = MockBufferedMailer.mailBuffer.size
        val data: Map[String,Seq[String]] = Map(
          "email" -> Seq(mocks.unprivilegedUser.email),
          CSRF.Conf.TOKEN_NAME -> Seq(fakeCsrfString)
        )
        val forgot = route(FakeRequest(POST,
          controllers.core.routes.Admin.forgotPasswordPost().url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString), data).get
        status(forgot) must equalTo(SEE_OTHER)
        MockBufferedMailer.mailBuffer.size must beEqualTo(numSentMails + 1)
        MockBufferedMailer.mailBuffer.last.to must contain(mocks.unprivilegedUser.email)

        val token = MockAccountDAO.tokens.last._1
        val resetForm = route(FakeRequest(GET,
          controllers.core.routes.Admin.resetPassword(token).url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString)).get
        status(resetForm) must equalTo(OK)

        val rstData: Map[String,Seq[String]] = Map(
          "password" -> Seq("hellokitty"),
          "confirm"  -> Seq("hellokitty"),
          CSRF.Conf.TOKEN_NAME -> Seq(fakeCsrfString)
        )
        val resetPost = route(FakeRequest(POST,
          controllers.core.routes.Admin.resetPassword(token).url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString), rstData).get
        status(resetPost) must equalTo(SEE_OTHER)

        val expired = route(FakeRequest(GET,
          controllers.core.routes.Admin.resetPassword(token).url)
          .withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString)).get
        status(expired) must equalTo(SEE_OTHER)
        val err = flash(expired).get("error")
        err must beSome
        err.get must equalTo(Messages("login.expiredOrInvalidResetToken"))
      }
    }
  }
}