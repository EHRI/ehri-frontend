package integration.admin

import play.api.i18n.{Lang, MessagesApi}
import play.api.inject._
import play.api.test._
import utils.{MockMovedPageLookup, MovedPageLookup}


class BrowserSpec extends PlaySpecification {

  private val buffer = collection.mutable.ArrayBuffer.empty[(String, String)]
  private val appBuilder = new play.api.inject.guice.GuiceApplicationBuilder()
    .overrides(bind[MovedPageLookup].toInstance(MockMovedPageLookup(buffer)))

  implicit def messagesApi(implicit app: play.api.Application): MessagesApi =
    app.injector.instanceOf[MessagesApi]

  "Application" should {

    "handle 404s properly for missing pages" in new WithBrowser(app = appBuilder.build()) {
      browser.goTo(controllers.admin.routes.Home.index().url + "/idontexist")
      browser.$("#error-title").text must_== messagesApi.apply("errors.pageNotFound")(Lang.defaultLang)
    }

    "return 301 for moved pages" in new WithBrowser(app = appBuilder.build()) {
      buffer += "/foo" -> "/bar"
      browser.goTo("/foo")
      browser.$("#error-title").text must_== messagesApi.apply("errors.pageNotFound")(Lang.defaultLang)
    }

    "deny access to admin routes" in new WithBrowser(app = appBuilder.build()) {
      browser.goTo(controllers.admin.routes.AdminSearch.search().url)
      browser.webDriver.getTitle must contain(messagesApi.apply("login.title")(Lang.defaultLang))
    }
  }
}
