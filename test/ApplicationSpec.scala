package test

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._

import helpers.TestMockLoginHelper
import play.api.i18n.Messages

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

    "redirect to login at admin home when secured" in {
      running(FakeApplication(withGlobal = Some(getGlobal),
          additionalConfiguration = Map("ehri.secured" -> true))) {
        val home = route(FakeRequest(GET,
          controllers.admin.routes.Home.index.url)).get
        status(home) must equalTo(SEE_OTHER)
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
          controllers.core.routes.OpenIDLoginHandler.openIDCallback.url)).get
        status(home) must equalTo(SEE_OTHER)
        val err = flash(home).get("error")
        err must beSome
        err.get must equalTo(Messages("openid.openIdError", null))
      }
    }
  }
}