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

    "work from within a browser" in {
      running(TestServer(3333, application = app), HTMLUNIT) { browser =>

        browser.goTo("http://localhost:3333/")
        // NB: This tests the Mock login form.
        browser.pageSource must contain("test login")

      }
    }

  }

}