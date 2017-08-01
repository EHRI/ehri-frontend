package services.redirects

import akka.actor.ActorSystem
import play.api.Application
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification


class SqlMovedPageLookupSpec extends PlaySpecification {
  private val app: Application = new GuiceApplicationBuilder().build()
  private implicit val actorSystem = app.injector.instanceOf[ActorSystem]
  private implicit def db = app.injector.instanceOf[Database]

  private def movedPageService(implicit db: Database) = SqlMovedPageLookup()(db, actorSystem)

  "Moved Page lookup" should {
    "fetch redirect items" in {
      await(movedPageService.addMoved(Seq("/old" -> "/new")))
      await(movedPageService.hasMovedTo("/old")) must beSome.which { n =>
        n must_== "/new"
      }
    }

    "update on confict items" in {
      await(movedPageService.addMoved(Seq("/old" -> "/new")))
      await(movedPageService.addMoved(Seq("/old" -> "/newer")))
      await(movedPageService.hasMovedTo("/old")) must beSome.which { n =>
        n must_== "/newer"
      }
    }
  }
}
