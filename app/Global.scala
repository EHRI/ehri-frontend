/**
 * The application global object.
 */

import backend.parse.ParseFeedbackDAO
import backend.{IdGenerator, FeedbackDAO, EventHandler, Backend}
import backend.rest._
import com.github.seratch.scalikesolr.request.common.WriterType
import com.typesafe.plugin.{CommonsMailerPlugin, MailerAPI}
import models.AccountDAO
import models.sql.SqlAccount
import play.api._

import play.api.mvc.{RequestHeader, WithFilters, Result}
import play.filters.csrf._

import com.tzavellas.sse.guice.ScalaModule
import utils.search._
import global.GlobalConfig
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


object Global extends WithFilters(CSRFFilter()) with GlobalSettings {

  import play.api.Play.current

  // This is where we tie together the various parts of the application
  // in terms of the component implementations.
  private def queryBuilder: QueryBuilder = new solr.SolrQueryBuilder(WriterType.JSON, debugQuery = true)
  private def searchDispatcher: Dispatcher = new solr.SolrDispatcher(
    queryBuilder,
    handler = r => new solr.SolrJsonQueryResponse(r.json)
  )
  private def searchIndexer: Indexer = new indexing.CmdlineIndexer
  private def searchResolver: Resolver = new GidSearchResolver
  private def feedbackDAO: FeedbackDAO = new ParseFeedbackDAO
  private def idGenerator: IdGenerator = new CypherIdGenerator(idFormat = "%06d")
  private def userDAO: AccountDAO = SqlAccount
  private def mailer: MailerAPI = new CommonsMailerPlugin(current).email

  private val eventHandler = new EventHandler {

    // Bind the EntityDAO Create/Update/Delete actions
    // to the SolrIndexer update/delete handlers. Do this
    // asyncronously and log any failures...
    import play.api.libs.concurrent.Execution.Implicits._
    import java.util.concurrent.TimeUnit
    import scala.concurrent.duration.Duration

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

  object RunConfiguration extends GlobalConfig

  class ProdModule extends ScalaModule {
    def configure() {
      bind[GlobalConfig].toInstance(RunConfiguration)
      bind[Indexer].toInstance(searchIndexer)
      bind[Dispatcher].toInstance(searchDispatcher)
      bind[Resolver].toInstance(searchResolver)
      bind[Backend].toInstance(backend)
      bind[FeedbackDAO].toInstance(feedbackDAO)
      bind[IdGenerator].toInstance(idGenerator)
      bind[MailerAPI].toInstance(mailer)
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

    ex.getCause match {
      case e: PermissionDenied => immediate(Unauthorized(
        renderError("errors.permissionDenied", permissionDenied(Some(e)))))
      case e: ItemNotFound => immediate(NotFound(
        renderError("errors.itemNotFound", itemNotFound(e.value))))
      case e: java.net.ConnectException => immediate(InternalServerError(
          renderError("errors.databaseError", serverTimeout())))
      case e: BadJson => sys.error(e.getMessageWithContext(request))

      case e => current.mode match {
        case Mode.Dev => super.onError(request, ex)
        case _ => immediate(InternalServerError(
          renderError("errors.genericProblem", fatalError())))
      }
    }
  }

  override def onHandlerNotFound(request: RequestHeader): Future[Result] = {
    implicit def req = request
    immediate(NotFound(renderError("errors.pageNotFound", pageNotFound())))
  }
}
