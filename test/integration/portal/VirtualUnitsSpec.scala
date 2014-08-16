package integration.portal

import helpers.Neo4jRunnerSpec
import controllers.portal.ReverseVirtualUnits
import models.BookmarkSet


class VirtualUnitsSpec extends Neo4jRunnerSpec(classOf[VirtualUnitsSpec]) {
  import mocks.privilegedUser

  private val vuRoutes: ReverseVirtualUnits = controllers.portal.routes.VirtualUnits

  private val data = Map(
    BookmarkSet.NAME -> Seq("Test User VU"),
    BookmarkSet.DESCRIPTION -> Seq("Some description")
  )
  
  "VirtualUnit views" should {
    "allow users to create VUs (simplified as bookmark sets)" in new FakeApp {
      val create = route(fakeLoggedInHtmlRequest(privilegedUser, POST, vuRoutes
        .createBookmarkSetPost(item = List("c4")).url), data).get
      status(create) must equalTo(SEE_OTHER)
      val list = route(fakeLoggedInHtmlRequest(privilegedUser,
        GET, vuRoutes.listBookmarkSets().url)).get
      contentAsString(list) must contain("Test User VU")
    }
  }
}
