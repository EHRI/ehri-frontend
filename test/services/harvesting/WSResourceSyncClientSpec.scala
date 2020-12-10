package services.harvesting

import akka.actor.ActorSystem
import akka.stream.Materializer
import helpers.TestConfiguration
import models.ResourceSyncConfig
import play.api.BuiltInComponentsFromContext
import play.api.routing.Router
import play.api.test.PlaySpecification
import play.filters.HttpFiltersComponents

class WSResourceSyncClientSpec extends PlaySpecification with TestConfiguration {

  private implicit val as: ActorSystem = ActorSystem("test")
  private implicit val mat: Materializer = Materializer(as)

  def withResourceSyncClient[T](block: WSResourceSyncClient => T): T = {
    import play.api.mvc._
    import play.api.routing.sird._
    import play.api.test._
    import play.core.server.Server
    Server.withApplicationFromContext() { context =>
      new BuiltInComponentsFromContext(context) with HttpFiltersComponents {
        override def router: Router = Router.from {
          case play.api.routing.sird.GET(p"/resourcesync/$file") =>
            Action { implicit req =>
              Results.Ok.sendResource(file)(executionContext, fileMimeTypes)
            }
        }
      }.application
    } { implicit port =>
      WsTestClient.withClient { client =>
        block(WSResourceSyncClient(client))
      }
    }
  }

  private val endpoint = ResourceSyncConfig("/resourcesync/capabilitylist.xml")

  "OAI RS client service" should {
    "list items" in withResourceSyncClient { client =>

      val list = await(client.list(endpoint))
      list.headOption must beSome.which { link =>
        link.loc must_== "/resourcesync/hierarchical-ead.xml"
      }
      list.size must_== 3
    }

    "list items with filter" in withResourceSyncClient { client =>
      val list = await(client.list(endpoint.copy(filter = Some("hier"))))
      list.headOption must beSome
      list.size must_== 1
    }

    "list items with RegExp filter" in withResourceSyncClient { client =>
      val list = await(client.list(endpoint.copy(filter = Some("test\\d\\.xml$"))))
      list.headOption must beSome
      list.size must_== 2
    }
  }
}
