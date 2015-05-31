package integration

import mocks.MockMovedPageLookup
import play.api.inject._
import play.api.test._
import play.api.i18n.{MessagesApi, Messages}
import utils.MovedPageLookup

class BrowserSpec extends PlaySpecification with play.api.i18n.I18nSupport {

  val appBuilder = new play.api.inject.guice.GuiceApplicationBuilder()
    .overrides(bind[MovedPageLookup].toInstance(MockMovedPageLookup()))

  implicit def messagesApi: MessagesApi =
    appBuilder.build().injector.instanceOf[play.api.i18n.MessagesApi]

  "Application" should {

    "handle 404s properly for missing pages" in new WithBrowser(app = appBuilder.build()) {
      browser.goTo(controllers.admin.routes.Home.index().url + "/idontexist")
      browser.$("#error-title").getTexts.get(0) must equalTo(Messages("errors.pageNotFound"))
    }

    "return 301 for moved pages" in new WithBrowser(app = appBuilder.build()) {
      mocks.movedPages += "/foo" -> "/bar"
      browser.goTo("/foo")
      browser.$("#error-title").getTexts.get(0) must equalTo(Messages("errors.pageNotFound"))
    }

    "deny access to admin routes" in new WithBrowser(app = appBuilder.build()) {
      browser.goTo(controllers.admin.routes.AdminSearch.search().url)
      browser.$("title").getTexts.get(0) must contain(Messages("login.title"))
    }
  }
}
