package test

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc.Cookie
import play.api.http.HeaderNames

import helpers.TestLoginHelper

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends Specification with TestLoginHelper {
  sequential

  "Application" should {
    "send 404 on a bad request" in {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "should display user email on login" in {
      running(fakeLoginApplication("mike")) {

        val home = route(fakeLoggedInRequest(GET, "/")).get
        status(home) must equalTo(OK)
        contentType(home) must beSome.which(_ == "text/html")

        contentAsString(home) must contain(mocks.MOCK_USER.email)
      }
    }

    "render the index page" in {
      running(FakeApplication()) {
        val home = route(FakeRequest(GET, "/")).get

        status(home) must equalTo(SEE_OTHER)
      }
    }
  }
}