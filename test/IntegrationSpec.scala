package test

import play.api.test._
import play.api.i18n.Messages

class IntegrationSpec extends PlaySpecification {

  "Application" should {

    // FIXME: For some reason, providing a FakeApplication here is
    // breaking the web driver...
    val app = FakeApplication(
      additionalPlugins = Seq("mocks.MockUserDAO")
    )

    // Cannot get this working property, still says url is on about:blank...
    "handle 404s properly for missing pages" in new WithBrowser {
      browser.goTo(controllers.admin.routes.Home.index.url + "/idontexist")
      browser.$("#error-title").getTexts().get(0) must equalTo(Messages("errors.pageNotFound"))
    }

    // FIXME: This is disabled because it requires running a DB in the background
    // Eventually we should either mock the db or start a real one.
    //"handle 404s properly for missing items" in new WithBrowser {
    //  // Testing a random portal doc route, because admin routes require logging in
    //  // and for some reason mocking the login isn't working...
    //  browser.goTo(controllers.portal.routes.Portal.browseDocument("idontexist").url)
    //  browser.$("#error-title").getTexts().get(0) must equalTo(Messages("errors.itemNotFound"))
    //}

    "deny access to admin routes" in new WithBrowser {
      browser.goTo(controllers.core.routes.UserProfiles.search.url)
      browser.$("title").getTexts.get(0) must equalTo(Messages("login.login"))
    }
  }
}
