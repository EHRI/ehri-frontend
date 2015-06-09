package integration.portal

import helpers.IntegrationTestRunner
import models.BookmarkSet
import play.api.test.FakeRequest


class BookmarksSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  private val bmRoutes = controllers.portal.routes.Bookmarks
  private val vuRoutes = controllers.portal.routes.VirtualUnits

  private val data = Map(
    BookmarkSet.NAME -> Seq("Test User VU"),
    BookmarkSet.DESCRIPTION -> Seq("Some description")
  )
  
  "Bookmark views" should {
    "create a default bookmark set when bookmarking an item" in new ITestApp {
      val bookmark1 = FakeRequest(bmRoutes.bookmarkPost("c1"))
        .withUser(privilegedUser).withCsrf.call()
      status(bookmark1) must equalTo(SEE_OTHER)
      val defId: String = s"${privilegedUser.id}-bookmarks"
      redirectLocation(bookmark1) must equalTo(Some(bmRoutes.listBookmarkSets().url))

      val bookmark2 = FakeRequest(bmRoutes.bookmark("c2", Some(defId)))
        .withUser(privilegedUser).withCsrf.call()
      redirectLocation(bookmark1) must equalTo(Some(bmRoutes.listBookmarkSets().url))
    }

    "create a named bookmark set when bookmarking an item" in new ITestApp {
      val bookmark1 = FakeRequest(bmRoutes.bookmarkInNewSetPost("c1"))
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(bookmark1) must equalTo(SEE_OTHER)

      redirectLocation(bookmark1) must beSome.which { rl =>
        val bs = FakeRequest(GET, rl).withUser(privilegedUser).call()
        contentAsString(bs) must contain("Test User VU")
      }
    }

    "allow users to create VUs (simplified as bookmark sets)" in new ITestApp {
      val create = FakeRequest(bmRoutes.createBookmarkSetPost(item = List("c4")))
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(create) must equalTo(SEE_OTHER)
      val list = FakeRequest(bmRoutes.listBookmarkSets()).withUser(privilegedUser).call()
      contentAsString(list) must contain("Test User VU")
    }
  }
}
