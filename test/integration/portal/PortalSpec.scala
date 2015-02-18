package integration.portal

import controllers.base.SessionPreferences
import helpers.IntegrationTestRunner
import controllers.portal.ReversePortal
import play.api.libs.json.Json
import play.api.test.{FakeApplication, FakeRequest}
import utils.SessionPrefs


class PortalSpec extends IntegrationTestRunner {
  import mocks.{privilegedUser, unprivilegedUser}

  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

  override def getConfig = Map("recaptcha.skip" -> true)

  "Portal views" should {
    "show index page" in new ITestApp {
      val doc = route(FakeRequest(GET, portalRoutes.index().url)).get
      status(doc) must equalTo(OK)
    }

    "send 301 when an item has been renamed" in new ITestApp {
      val oldRoute = controllers.portal.routes.DocumentaryUnits.browse("OLD")
      val newRoute = controllers.portal.routes.DocumentaryUnits.browse("NEW")
      val before = route(FakeRequest(oldRoute)).get
      status(before) must equalTo(NOT_FOUND)

      mocks.movedPages += oldRoute.url -> newRoute.url
      val rename = route(FakeRequest(oldRoute)).get
      status(rename) must equalTo(MOVED_PERMANENTLY)
      redirectLocation(rename) must equalTo(Some(newRoute.url))
    }

    "allow setting view preferences" in {
      running(FakeApplication(withGlobal = Some(getGlobal))) {
        val prefJson = route(FakeRequest(portalRoutes.prefs())).get
        (contentAsJson(prefJson) \ SessionPrefs.SHOW_USER_CONTENT).as[Boolean] must beTrue
        val setPrefs = route(fakeLoggedInRequest(privilegedUser, POST, portalRoutes.updatePrefs().url)
          .withFormUrlEncodedBody(SessionPrefs.SHOW_USER_CONTENT -> "false")).get
        status(setPrefs) must equalTo(SEE_OTHER)
        session(setPrefs).get(SessionPreferences.DEFAULT_STORE_KEY) must beSome.which {jsStr =>
          val json = Json.parse(jsStr)
          (json \ SessionPrefs.SHOW_USER_CONTENT).as[Boolean] must beFalse
        }
      }
    }

    "allow setting the language" in {
      running(FakeApplication(withGlobal = Some(getGlobal))) {
        val about = route(FakeRequest(portalRoutes.about())).get
        session(about).get(SessionPreferences.DEFAULT_STORE_KEY) must beNone
        val setLang = route(FakeRequest(portalRoutes.changeLocale("de"))).get
        status(setLang) must equalTo(SEE_OTHER)
        session(setLang).get(SessionPreferences.DEFAULT_STORE_KEY) must beSome.which { jsStr =>
          val json = Json.parse(jsStr)
          (json \ SessionPrefs.LANG).asOpt[String] must equalTo(Some("de"))
        }
      }
    }

    "view docs" in new ITestApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser,
        controllers.portal.routes.DocumentaryUnits.browse("c1"))).get
      status(doc) must equalTo(OK)
    }

    "view repositories" in new ITestApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser,
        controllers.portal.routes.Repositories.browse("r1"))).get
      status(doc) must equalTo(OK)
    }

    "view historical agents" in new ITestApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser,
        controllers.portal.routes.HistoricalAgents.browse("a1"))).get
      status(doc) must equalTo(OK)
    }
    
    "view item history" in new ITestApp {
      val history = route(fakeLoggedInHtmlRequest(privilegedUser,
        portalRoutes.itemHistory("c1"))).get
      status(history) must equalTo(OK)
    }
  }
}
