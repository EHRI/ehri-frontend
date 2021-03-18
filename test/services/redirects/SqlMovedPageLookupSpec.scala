package services.redirects

import helpers.{SimpleAppTest, withDatabase}
import play.api.db.Database
import play.api.test.PlaySpecification


class SqlMovedPageLookupSpec extends SimpleAppTest with PlaySpecification {
  private def movedPageService(implicit db: Database) = SqlMovedPageLookup(db)(implicitApp.actorSystem)

  "Moved Page lookup" should {
    "fetch redirect items" in withDatabase { implicit db =>
      await(movedPageService.addMoved(Seq("/old" -> "/new")))
      await(movedPageService.hasMovedTo("/old")) must beSome.which { n =>
        n must_== "/new"
      }
    }

    "update on confict items" in withDatabase { implicit db =>
      await(movedPageService.addMoved(Seq("/old" -> "/new")))
      await(movedPageService.addMoved(Seq("/old" -> "/newer")))
      await(movedPageService.hasMovedTo("/old")) must beSome.which { n =>
        n must_== "/newer"
      }
    }
  }
}
