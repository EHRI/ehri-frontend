package integration.portal

import helpers.Neo4jRunnerSpec
import controllers.portal.ReverseVirtualUnits
import models.BookmarkSet


class BookmarksSpec extends Neo4jRunnerSpec(classOf[BookmarksSpec]) {
  import mocks.privilegedUser

  private val bmRoutes = controllers.portal.routes.Bookmarks
  private val vuRoutes = controllers.portal.routes.VirtualUnits

  private val data = Map(
    BookmarkSet.NAME -> Seq("Test User VU"),
    BookmarkSet.DESCRIPTION -> Seq("Some description")
  )
  
  "Bookmark views" should {
    "create a default bookmark set when bookmarking an item" in new FakeApp {
      val bookmark1 = route(fakeLoggedInHtmlRequest(privilegedUser, POST, bmRoutes
        .bookmarkPost("c1").url), "").get
      status(bookmark1) must equalTo(SEE_OTHER)
      val defId: String = s"${privilegedUser.id}-bookmarks"
      redirectLocation(bookmark1) must equalTo(Some(vuRoutes
        .browseVirtualCollection(defId).url))

      val bookmark2 = route(fakeLoggedInHtmlRequest(privilegedUser, POST, bmRoutes
        .bookmark("c2", Some(defId)).url), "").get
      redirectLocation(bookmark1) must equalTo(Some(vuRoutes
        .browseVirtualCollection(defId).url))
    }

    "create a named bookmark set when bookmarking an item" in new FakeApp {
      val bookmark1 = route(fakeLoggedInHtmlRequest(privilegedUser, POST, bmRoutes
        .bookmarkInNewSetPost("c1").url), data).get
      status(bookmark1) must equalTo(SEE_OTHER)

      redirectLocation(bookmark1) must beSome.which { rl =>
        val bs = route(fakeLoggedInHtmlRequest(privilegedUser, GET, rl)).get
        contentAsString(bs) must contain("Test User VU")
      }
    }

    "allow users to create VUs (simplified as bookmark sets)" in new FakeApp {
      val create = route(fakeLoggedInHtmlRequest(privilegedUser, POST, bmRoutes
        .createBookmarkSetPost(item = List("c4")).url), data).get
      status(create) must equalTo(SEE_OTHER)
      val list = route(fakeLoggedInHtmlRequest(privilegedUser,
        GET, bmRoutes.listBookmarkSets().url)).get
      contentAsString(list) must contain("Test User VU")
    }
  }
}
