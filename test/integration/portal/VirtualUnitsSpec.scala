package integration.portal

import helpers.IntegrationTestRunner
import controllers.portal.ReverseVirtualUnits


class VirtualUnitsSpec extends IntegrationTestRunner {
  import mocks.privilegedUser

  private val vuRoutes: ReverseVirtualUnits = controllers.portal.routes.VirtualUnits

  "VirtualUnit views" should {
    "render virtual units" in new ITestApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser,
        vuRoutes.browseVirtualCollection("vc1"))).get
      contentAsString(show) must contain("Virtual Collection 1")
      val search = route(fakeLoggedInHtmlRequest(privilegedUser,
        vuRoutes.searchVirtualCollection("vc1"))).get
      contentAsString(search) must contain(vuRoutes.browseVirtualUnit("vc1", "vu1").url)
    }
  }
}
