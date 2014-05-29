package integration

import play.api.test._
import play.api.i18n.Messages

class BrowserSpec extends PlaySpecification {

  "Application" should {

    "handle 404s properly for missing pages" in new WithBrowser {
      browser.goTo(controllers.admin.routes.Home.index().url + "/idontexist")
      browser.$("#error-title").getTexts.get(0) must equalTo(Messages("errors.pageNotFound"))
    }

    "deny access to admin routes" in new WithBrowser {
      browser.goTo(controllers.admin.routes.AdminSearch.search().url)
      browser.$("title").getTexts.get(0) must equalTo(Messages("portal.login"))
    }
  }
}
