//
// Global request object
//

import _root_.controllers.core.OpenIDLoginHandler
import _root_.models.{HistoricalAgent, Repository, DocumentaryUnit, Concept}
import defines.EntityType
import global.{GlobalConfig, MenuConfig, RouteRegistry}
import indexing.CmdlineIndexer
import java.io.File
import play.api._
import play.api.mvc._

import org.apache.commons.codec.binary.Base64

import play.api.Play.current
import play.filters.csrf.CSRFFilter
import rest.EntityDAO
import solr.{SolrErrorResponse, SolrIndexer}

import com.tzavellas.sse.guice.ScalaModule
import utils.search.{Indexer, Dispatcher}
import global.GlobalConfig


/**
 * Filter that applies CSRF protection unless a particular
 * custom header is present. The value of the header is
 * not checked.
 */
class AjaxCSRFFilter extends EssentialFilter {
  var csrfFilter = new CSRFFilter()

  val AJAX_HEADER_TOKEN = "ajax-ignore-csrf"

  def apply(next: EssentialAction) = new EssentialAction {
    def apply(request: RequestHeader) = {
      if (request.headers.keys.contains(AJAX_HEADER_TOKEN))
        next(request)
      else
        csrfFilter(next)(request)
    }
  }
}

package globalconfig {

  import global.RouteRegistry

  object RunConfiguration extends GlobalConfig {

    val searchDispatcher: Dispatcher = solr.SolrDispatcher()

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
    }

    // Implicit 'this' var so we can have a circular reference
    // to the current global inside the login handler.
    private implicit lazy val globalConfig = this
    val loginHandler = new OpenIDLoginHandler

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
    ), default = controllers.admin.routes.Home.index)
  }
}


object Global extends WithFilters(new AjaxCSRFFilter()) with GlobalSettings {

  private def searchIndexer: indexing.NewIndexer = new indexing.CmdlineIndexer

  class ProdModule extends ScalaModule {
    def configure() {
      bind[GlobalConfig].toInstance(globalconfig.RunConfiguration)
      bind[indexing.NewIndexer].toInstance(searchIndexer)
    }
  }

  private lazy val injector = {
    com.google.inject.Guice.createInjector(new ProdModule)
  }

  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

  override def onStart(app: Application) {

    // Check the indexer is configured...
    if (!new File(CmdlineIndexer.jar).exists()) {
      sys.error("Unable to find jar for indexer: " + CmdlineIndexer.jar)
    }

    // Hack for bug #845
    app.routes

    // Bind the EntityDAO Create/Update/Delete actions
    // to the SolrIndexer update/delete handlers
    import play.api.libs.concurrent.Execution.Implicits._

    EntityDAO.addCreateHandler(searchIndexer.indexId)

    EntityDAO.addUpdateHandler(searchIndexer.indexId)

    EntityDAO.addDeleteHandler(searchIndexer.clearId)
  }

  private def noAuthAction = Action { request =>
    play.api.mvc.Results.Unauthorized("This application required authentication")
      .withHeaders("WWW-Authenticate" -> "Basic")
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {

    val usernameOpt = current.configuration.getString("auth.basic.username")
    val passwordOpt = current.configuration.getString("auth.basic.password")
    if (usernameOpt.isDefined && passwordOpt.isDefined) {
      for {
        username <- usernameOpt
        password <- passwordOpt
        authstr <- request.headers.get("Authorization")
        base64 <- authstr.split(" ").drop(1).headOption
        authvals = new String(Base64.decodeBase64(base64.getBytes))
      } yield {
        authvals.split(":").toList match {
          case u :: p :: Nil if u == username && p == password => super.onRouteRequest(request)
          case _ => Some(noAuthAction)
        }
      }.getOrElse(noAuthAction)

    } else {
      super.onRouteRequest(request)
    }
  }
}