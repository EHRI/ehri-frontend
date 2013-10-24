//
// Global request object
//

import defines.EntityType
import java.util.concurrent.TimeUnit
import play.api._
import play.api.mvc._

import play.api.Play.current
import play.filters.csrf._
import rest.RestEventHandler
import scala.concurrent.duration.Duration
import scala.concurrent.Future

import com.tzavellas.sse.guice.ScalaModule
import utils.search.{Indexer, Dispatcher}
import global.GlobalConfig


package globalConfig {

  import global.RouteRegistry
  import global.MenuConfig

  trait BaseConfiguration extends GlobalConfig {

    implicit lazy val menuConfig: MenuConfig = new MenuConfig {
      val mainSection: Iterable[(String, String)] = Seq(
        ("pages.search",                  controllers.admin.routes.Search.search.url),
        ("contentTypes.documentaryUnit",  controllers.archdesc.routes.DocumentaryUnits.search.url),
        ("contentTypes.historicalAgent",  controllers.authorities.routes.HistoricalAgents.search.url),
        ("contentTypes.repository",       controllers.archdesc.routes.Repositories.search.url),
        ("contentTypes.cvocConcept",      controllers.vocabs.routes.Concepts.search.url)
      )
      val adminSection: Iterable[(String, String)] = Seq(
        ("contentTypes.userProfile",      controllers.core.routes.UserProfiles.search.url),
        ("contentTypes.group",            controllers.core.routes.Groups.list.url),
        ("contentTypes.country",          controllers.archdesc.routes.Countries.search.url),
        ("contentTypes.cvocVocabulary",   controllers.vocabs.routes.Vocabularies.list.url),
        ("contentTypes.authoritativeSet", controllers.authorities.routes.AuthoritativeSets.list.url),
        ("s1", "-"),
        ("contentTypes.systemEvent",      controllers.core.routes.SystemEvents.list.url),
        ("s2", "-"),
        ("search.updateIndex",            controllers.admin.routes.Search.updateIndex.url)
      )
      val authSection: Iterable[(String,String)] = Seq(
        ("actions.viewProfile", controllers.admin.routes.Profile.profile.url),
        ("login.changePassword", controllers.core.routes.Admin.changePassword.url)
      )
    }

    val routeRegistry = new RouteRegistry(Map(
      EntityType.SystemEvent -> controllers.core.routes.SystemEvents.get _,
      EntityType.DocumentaryUnit -> controllers.archdesc.routes.DocumentaryUnits.get _,
      EntityType.HistoricalAgent -> controllers.authorities.routes.HistoricalAgents.get _,
      EntityType.Repository -> controllers.archdesc.routes.Repositories.get _,
      EntityType.Group -> controllers.core.routes.Groups.get _,
      EntityType.UserProfile -> controllers.core.routes.UserProfiles.get _,
      EntityType.Annotation -> controllers.annotation.routes.Annotations.get _,
      EntityType.Link -> controllers.linking.routes.Links.get _,
      EntityType.Vocabulary -> controllers.vocabs.routes.Vocabularies.get _,
      EntityType.AuthoritativeSet -> controllers.authorities.routes.AuthoritativeSets.get _,
      EntityType.Concept -> controllers.vocabs.routes.Concepts.get _,
      EntityType.Country -> controllers.archdesc.routes.Countries.get _
    ), default = controllers.admin.routes.Home.index,
      login = controllers.core.routes.Admin.login,
      logout = controllers.core.routes.Admin.logout)
  }
}

// FIXME: using WithFilters(new AjaxCSRFFilter) breaks things here...

object Global extends WithFilters(new CSRFFilter()) with GlobalSettings {

  private def searchDispatcher: Dispatcher = new solr.SolrDispatcher
  private def searchIndexer: Indexer = new indexing.CmdlineIndexer

  object RunConfiguration extends globalConfig.BaseConfiguration {
    implicit val eventHandler: rest.RestEventHandler = new RestEventHandler {

      // Bind the EntityDAO Create/Update/Delete actions
      // to the SolrIndexer update/delete handlers. Do this
      // asyncronously and log any failures...
      import play.api.libs.concurrent.Execution.Implicits._
      def logFailure(id: String, func: String => Future[Unit]): Unit = {
        func(id) onFailure {
          case t => Logger.logger.error("Indexing error: " + t.getMessage)
        }
      }

      def handleCreate(id: String) = logFailure(id, searchIndexer.indexId)
      def handleUpdate(id: String) = logFailure(id, searchIndexer.indexId)

      // Special case - block when deleting because otherwise we get ItemNotFounds
      // after redirects
      def handleDelete(id: String) = logFailure(id, id => Future.successful[Unit] {
        concurrent.Await.result(searchIndexer.clearId(id), Duration(1, TimeUnit.MINUTES))
      })
    }
  }


  class ProdModule extends ScalaModule {
    def configure() {
      bind[GlobalConfig].toInstance(RunConfiguration)
      bind[Indexer].toInstance(searchIndexer)
      bind[Dispatcher].toInstance(searchDispatcher)
    }
  }

  private lazy val injector = {
    com.google.inject.Guice.createInjector(new ProdModule)
  }

  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

  override def onStart(app: Application) {

    // Hack for bug #845
    app.routes
  }
}