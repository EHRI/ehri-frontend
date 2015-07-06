package integration.portal

import controllers.base.SessionPreferences
import controllers.portal.ReversePortal
import helpers.IntegrationTestRunner
import play.api.libs.json.Json
import play.api.test.FakeRequest
import utils.SessionPrefs


class PortalSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

  override def getConfig = Map("recaptcha.skip" -> true)

  "Portal views" should {
    "show index page" in new ITestApp {
      val doc = FakeRequest(portalRoutes.index()).call()
      status(doc) must equalTo(OK)
    }

    "show index page in other languages" in new ITestApp {
      val doc = FakeRequest(portalRoutes.index())
        .withPreferences(new SessionPrefs(language = Some("fr")))
        .call()
      contentAsString(doc) must contain("Bienvenue sur")
    }

    "send 301 when an item has been renamed" in new ITestApp {
      val oldRoute = controllers.portal.routes.DocumentaryUnits.browse("OLD")
      val newRoute = controllers.portal.routes.DocumentaryUnits.browse("NEW")
      val before = FakeRequest(oldRoute).call()
      status(before) must equalTo(NOT_FOUND)

      mockdata.movedPages += oldRoute.url -> newRoute.url
      val rename = FakeRequest(oldRoute).call()
      status(rename) must equalTo(MOVED_PERMANENTLY)
      redirectLocation(rename) must equalTo(Some(newRoute.url))
    }

    "allow setting view preferences" in new ITestApp {
      val prefJson = FakeRequest(portalRoutes.prefs()).withUser(privilegedUser).call()
      (contentAsJson(prefJson) \ SessionPrefs.SHOW_USER_CONTENT).as[Boolean] must beTrue
      val setPrefs = FakeRequest(portalRoutes.updatePrefs()).withUser(privilegedUser)
        .withFormUrlEncodedBody(SessionPrefs.SHOW_USER_CONTENT -> "false").withCsrf.call()
      status(setPrefs) must equalTo(SEE_OTHER)
      session(setPrefs).get(SessionPreferences.DEFAULT_STORE_KEY) must beSome.which {jsStr =>
        val json = Json.parse(jsStr)
        (json \ SessionPrefs.SHOW_USER_CONTENT).as[Boolean] must beFalse
      }
    }

    "allow setting the language" in new ITestApp {
      val about = FakeRequest(portalRoutes.about()).call()
      session(about).get(SessionPreferences.DEFAULT_STORE_KEY) must beNone
      val setLang = FakeRequest(portalRoutes.changeLocale("de")).call()
      status(setLang) must equalTo(SEE_OTHER)
      session(setLang).get(SessionPreferences.DEFAULT_STORE_KEY) must beSome.which { jsStr =>
        val json = Json.parse(jsStr)
        (json \ SessionPrefs.LANG).asOpt[String] must equalTo(Some("de"))
      }
    }

    "view docs" in new ITestApp {
      val doc = FakeRequest(controllers.portal.routes.DocumentaryUnits.browse("c4")).call()
      status(doc) must equalTo(OK)
    }

    "view repositories" in new ITestApp {
      val doc = FakeRequest(controllers.portal.routes.Repositories.browse("r1")).call()
      status(doc) must equalTo(OK)
    }

    "view historical agents" in new ITestApp {
      val doc = FakeRequest(controllers.portal.routes.HistoricalAgents.browse("a1")).call()
      status(doc) must equalTo(OK)
    }
    
    "view item history" in new ITestApp {
      val history = FakeRequest(portalRoutes.itemHistory("c4")).call()
      status(history) must equalTo(OK)
    }

    "fetch external pages" in new ITestApp {
      val faq = FakeRequest(portalRoutes.externalPage("faq")).call()
      status(faq) must equalTo(OK)
      contentAsString(faq) must contain(mockdata.externalPages.get("faq").get.toString())
    }

    "return 404 for external pages with a malformed id (bug #635)" in new ITestApp {
      val faq = FakeRequest(portalRoutes.externalPage(",b.name,k.length")).call()
      status(faq) must equalTo(NOT_FOUND)
    }
  }
}
