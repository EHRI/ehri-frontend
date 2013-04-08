package test

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._

import play.api.GlobalSettings

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends Specification {
  sequential
  object SimpleFakeGlobal extends GlobalSettings
  "Application" should {
    "send 404 on a bad request" in {
      running(FakeApplication(withGlobal = Some(SimpleFakeGlobal))) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "redirect to login page when called afresh" in {
      running(FakeApplication(withGlobal = Some(SimpleFakeGlobal))) {
        val home = route(FakeRequest(GET, "/")).get

        status(home) must equalTo(SEE_OTHER)
      }
    }
  }
}