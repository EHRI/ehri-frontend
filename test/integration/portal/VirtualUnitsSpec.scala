package integration.portal

import helpers.IntegrationTestRunner
import controllers.portal.ReverseVirtualUnits
import play.api.test.FakeRequest


class VirtualUnitsSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  private val vuRoutes: ReverseVirtualUnits = controllers.portal.routes.VirtualUnits

  "VirtualUnit views" should {
    "render virtual units" in new ITestApp {
      val show = FakeRequest(vuRoutes.browseVirtualCollection("vc1"))
        .withUser(privilegedUser).call()
      contentAsString(show) must contain("Virtual Collection 1")
      val search = FakeRequest(vuRoutes.searchVirtualCollection("vc1"))
        .withUser(privilegedUser).call()
      contentAsString(search) must contain(vuRoutes.browseVirtualUnit("vc1", "vu1").url)
    }

    "display children with no query" in new ITestApp {
      val search = FakeRequest(vuRoutes.searchVirtualCollection("vc1"))
        .withUser(privilegedUser).call()
      status(search) must equalTo(OK)
    }

    "display children with query" in new ITestApp {
      val search = FakeRequest(GET, vuRoutes.searchVirtualCollection("vc1").url + "?q=test")
        .withUser(privilegedUser).call()
      status(search) must equalTo(OK)
    }
  }
}
