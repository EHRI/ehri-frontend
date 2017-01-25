package integration.admin

import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.inject._
import play.api.test._
import utils.{MockMovedPageLookup, MovedPageLookup}

class BrowserSpec extends PlaySpecification {

  private val buffer = collection.mutable.ArrayBuffer.empty[(String, String)]
  private val appBuilder = new play.api.inject.guice.GuiceApplicationBuilder()
    .overrides(bind[MovedPageLookup].toInstance(MockMovedPageLookup(buffer)))

  "Application" should {

    "handle 404s properly for missing pages" in new WithBrowser(app = appBuilder.build()) {
      browser.goTo(controllers.admin.routes.Home.index().url + "/idontexist")
      browser.$("#error-title").getTexts.get(0) must equalTo(Messages("errors.pageNotFound"))
    }

    "return 301 for moved pages" in new WithBrowser(app = appBuilder.build()) {
      buffer += "/foo" -> "/bar"
      browser.goTo("/foo")
      browser.$("#error-title").getTexts.get(0) must equalTo(Messages("errors.pageNotFound"))
    }

    "deny access to admin routes" in new WithBrowser(app = appBuilder.build()) {
      browser.goTo(controllers.admin.routes.AdminSearch.search().url)
      browser.title() must contain(Messages("login.title"))
    }
  }
}
