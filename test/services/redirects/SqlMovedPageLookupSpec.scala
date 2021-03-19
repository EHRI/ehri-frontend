package services.redirects

import helpers.IntegrationTestRunner
import play.api.Application


class SqlMovedPageLookupSpec extends IntegrationTestRunner {
  private def movedPageService(implicit app: Application) = app.injector.instanceOf[SqlMovedPageLookup]

  "Moved Page lookup" should {
    "fetch redirect items" in new DBTestApp {
      await(movedPageService.addMoved(Seq("/old" -> "/new")))
      await(movedPageService.hasMovedTo("/old")) must beSome.which { n =>
        n must_== "/new"
      }
    }

    "update on confict items" in new DBTestApp {
      await(movedPageService.addMoved(Seq("/old" -> "/new")))
      await(movedPageService.addMoved(Seq("/old" -> "/newer")))
      await(movedPageService.hasMovedTo("/old")) must beSome.which { n =>
        n must_== "/newer"
      }
    }
  }
}
