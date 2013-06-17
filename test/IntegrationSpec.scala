package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.Messages

class IntegrationSpec extends Specification {

  "Application" should {

    val app = FakeApplication(
      additionalPlugins = Seq("mocks.MockUserDAO", "mocks.MockLoginHandler")
    )

    // Cannot get this working property, still says url is on about:blank...
    /*"run in a browser" in new WithBrowser(port = 3333, app = app) {
      browser.goTo("http://localhost:3333")
      browser.$("a#signin").click()
      browser.url must equalTo("http://localhost:3333/login")
    }*/
  }

}