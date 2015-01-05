package integration.portal

import helpers.IntegrationTestRunner
import controllers.portal.ReversePortal
import play.api.test.FakeRequest


class PortalSpec extends IntegrationTestRunner {
  import mocks.{privilegedUser, unprivilegedUser}

  private val portalRoutes: ReversePortal = controllers.portal.routes.Portal

  override def getConfig = Map("recaptcha.skip" -> true)

  "Portal views" should {
    "show index page" in new ITestApp {
      val doc = route(FakeRequest(GET, portalRoutes.index().url)).get
      status(doc) must equalTo(OK)
    }

    "view docs" in new ITestApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.DocumentaryUnits.browse("c1").url)).get
      status(doc) must equalTo(OK)
    }

    "view repositories" in new ITestApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.Repositories.browse("r1").url)).get
      status(doc) must equalTo(OK)
    }

    "view historical agents" in new ITestApp {
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.portal.routes.HistoricalAgents.browse("a1").url)).get
      status(doc) must equalTo(OK)
    }
    
    "view item history" in new ITestApp {
      val history = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        portalRoutes.itemHistory("c1").url)).get
      status(history) must equalTo(OK)
    }
  }
}
