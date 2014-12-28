package integration.portal

import helpers.IntegrationTestRunner
import controllers.portal.ReverseVirtualUnits


class VirtualUnitsSpec extends IntegrationTestRunner {
  import mocks.privilegedUser

  private val vuRoutes: ReverseVirtualUnits = controllers.portal.routes.VirtualUnits

  "VirtualUnit views" should {
    "render virtual units" in new ITestApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser,
        GET, vuRoutes.browseVirtualCollection("vc1").url)).get
      contentAsString(show) must contain("Virtual Collection 1")
      val search = route(fakeLoggedInHtmlRequest(privilegedUser,
        GET, vuRoutes.searchVirtualCollection("vc1").url)).get
      contentAsString(search) must contain(vuRoutes.browseVirtualUnit("vc1", "vu1").url)
    }
  }
}
