package test

import org.specs2.mutable._

import play.api.test._

class IntegrationSpec extends PlaySpecification {

  "Application" should {

    val app = FakeApplication(
      additionalPlugins = Seq("mocks.MockUserDAO")
    )

    // Cannot get this working property, still says url is on about:blank...
    "handle 404s property" in new WithBrowser(port = 3333, app = app) {
      browser.goTo("http://localhost:3333")
    }
  }

}