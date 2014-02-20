//
// Global request object
//

import backend.parse.ParseFeedbackDAO
import backend.rest._
import backend.rest.BadJson
import backend.rest.RestBackend
import backend.rest.SearchResolver
import backend.{IdGenerator, FeedbackDAO, EventHandler, Backend}
import defines.EntityType
import java.util.concurrent.TimeUnit
import models.AccountDAO
import models.sql.SqlAccount
import play.api._
import play.api.libs.json.{Json, JsPath, JsError}
import play.api.mvc._

import play.api.mvc.SimpleResult
import play.api.Play.current
import play.api.templates.Html
import play.filters.csrf._
import scala.concurrent.duration.Duration

import com.tzavellas.sse.guice.ScalaModule
import utils.search._
import global.GlobalConfig
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import views.html.errors.itemNotFound
import views.html.layout.errorLayout


package globalConfig {

  import global.RouteRegistry
  import global.MenuConfig

  trait BaseConfiguration extends GlobalConfig {

    implicit lazy val menuConfig: MenuConfig = new MenuConfig {
      val mainSection: Iterable[(String, String)] = Seq(
        ("pages.search",                  controllers.admin.routes.AdminSearch.search.url),
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
        ("search.updateIndex",            controllers.admin.routes.AdminSearch.updateIndex.url)
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

object Global extends WithFilters(CSRFFilter()) with GlobalSettings {

  private def responseParser: ResponseParser = solr.SolrXmlQueryResponse
  private def queryBuilder: QueryBuilder = new solr.SolrQueryBuilder(responseParser.writerType)
  private def searchDispatcher: Dispatcher = new solr.SolrDispatcher(queryBuilder, responseParser)
  private def searchIndexer: Indexer = new indexing.CmdlineIndexer
  private def searchResolver: Resolver = new SearchResolver
  private def feedbackDAO: FeedbackDAO = new ParseFeedbackDAO
  private def idGenerator: IdGenerator = new CypherIdGenerator(idFormat = "%06d")
  private def userDAO: AccountDAO = SqlAccount

  private val eventHandler = new EventHandler {

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

  private def backend: Backend = new RestBackend(eventHandler)

  object RunConfiguration extends globalConfig.BaseConfiguration


  class ProdModule extends ScalaModule {
    def configure() {
      bind[GlobalConfig].toInstance(RunConfiguration)
      bind[Indexer].toInstance(searchIndexer)
      bind[Dispatcher].toInstance(searchDispatcher)
      bind[Resolver].toInstance(searchResolver)
      bind[Backend].toInstance(backend)
      bind[FeedbackDAO].toInstance(feedbackDAO)
      bind[IdGenerator].toInstance(idGenerator)
      bind[AccountDAO].toInstance(userDAO)
    }
  }

  private lazy val injector = {
    com.google.inject.Guice.createInjector(new ProdModule)
  }

  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

  import play.api.mvc.Results._
  import views.html.errors._
  import utils.renderError

  override def onError(request: RequestHeader, ex: Throwable) = {
    implicit def req = request

    def jsonError(err: Seq[(JsPath,Seq[play.api.data.validation.ValidationError])]) = {
      "Unexpected JSON received from backend at %s (%s)\n\n%s".format(
        request.path, request.method, Json.prettyPrint(JsError.toFlatJson(err))
      )
    }

    ex.getCause match {
      case e: PermissionDenied => immediate(Unauthorized(
        renderError("errors.permissionDenied", permissionDenied(Some(e)))))
      case e: ItemNotFound => immediate(NotFound(
        renderError("errors.itemNotFound", itemNotFound(e.value))))
      case e: java.net.ConnectException => immediate(InternalServerError(
          renderError("errors.databaseError", serverTimeout())))

      case BadJson(err) => sys.error(jsonError(err))
      case e => super.onError(request, e)
    }
  }

  override def onHandlerNotFound(request: RequestHeader): Future[SimpleResult] = {
    implicit def req = request
    immediate(NotFound(renderError("errors.pageNotFound", pageNotFound())))
  }
}
