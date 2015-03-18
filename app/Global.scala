/**
 * The application global object.
 */

import auth.AccountManager
import auth.oauth2.{WebOAuth2Flow, OAuth2Flow}
import auth.sql.SqlAccountManager
import backend.aws.S3FileStorage
import backend.googledocs.GoogleDocsHtmlPages
import backend.helpdesk.EhriHelpdesk
import backend.parse.ParseFeedbackDAO
import backend.rest._
import backend.{Backend, EventHandler, FeedbackDAO, IdGenerator, _}
import com.google.inject.{AbstractModule, Guice}
import com.typesafe.plugin.{CommonsMailerPlugin, MailerAPI}
import controllers.base.SessionPreferences
import global.GlobalConfig
import play.api._
import play.api.i18n.Lang
import play.api.mvc.{RequestHeader, Result, WithFilters}
import play.filters.csrf._
import eu.ehri.project.search.solr.{SolrSearchEngine, SolrQueryBuilder,JsonResponseHandler,WriterType}
import utils.{SessionPrefs, DbMovedPageLookup, MovedPageLookup}
import utils.search._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


object Global extends WithFilters(CSRFFilter()) with GlobalSettings with SessionPreferences[SessionPrefs] {

  import play.api.Play.current

  // This is where we tie together the various parts of the application
  // in terms of the component implementations.
  private def searchEngine: SearchEngine = new SolrSearchEngine(
    new SolrQueryBuilder(WriterType.Json, debugQuery = true),
    JsonResponseHandler
  )
  private def searchIndexer: SearchIndexer = new indexing.CmdlineIndexer
  private def searchResolver: SearchItemResolver = new GidSearchResolver
  private def feedbackDAO: FeedbackDAO = new ParseFeedbackDAO
  private def helpdeskDAO: HelpdeskDAO = new EhriHelpdesk
  private def idGenerator: IdGenerator = new CypherIdGenerator(idFormat = "%06d")
  private def accounts: AccountManager = SqlAccountManager()
  private def mailer: MailerAPI = new CommonsMailerPlugin(current).email
  private def oAuth2Flow: OAuth2Flow = new WebOAuth2Flow()
  private def relocator: MovedPageLookup = new DbMovedPageLookup()
  private def fileStorage: FileStorage = new S3FileStorage()
  private def htmlPages: HtmlPages = new GoogleDocsHtmlPages()

  private val eventHandler = new EventHandler {

    // Bind the EntityDAO Create/Update/Delete actions
    // to the SolrIndexer update/delete handlers. Do this
    // asynchronously and log any failures...
    import java.util.concurrent.TimeUnit
    import scala.concurrent.duration.Duration
    import play.api.libs.concurrent.Execution.Implicits._

    def logFailure(id: String, func: String => Future[Unit]): Unit = {
      func(id) onFailure {
        case t => Logger.logger.error("Indexing error: " + t.getMessage)
      }
    }

    def handleCreate(id: String) = logFailure(id, searchIndexer.indexId)
    def handleUpdate(id: String) = logFailure(id, searchIndexer.indexId)

    // Special case - block when deleting because otherwise we get ItemNotFounds
    // after redirects because the item is still in the search index but not in
    // the database.
    def handleDelete(id: String) = logFailure(id, id => Future.successful[Unit] {
      concurrent.Await.result(searchIndexer.clearId(id), Duration(1, TimeUnit.MINUTES))
    })
  }

  private def backend: Backend = new RestBackend(eventHandler)

  object RunConfiguration extends GlobalConfig

  lazy val injector = Guice.createInjector(new AbstractModule {
    protected def configure() {
      bind(classOf[GlobalConfig]).toInstance(RunConfiguration)
      bind(classOf[SearchIndexer]).toInstance(searchIndexer)
      bind(classOf[SearchEngine]).toInstance(searchEngine)
      bind(classOf[SearchItemResolver]).toInstance(searchResolver)
      bind(classOf[Backend]).toInstance(backend)
      bind(classOf[FeedbackDAO]).toInstance(feedbackDAO)
      bind(classOf[HelpdeskDAO]).toInstance(helpdeskDAO)
      bind(classOf[IdGenerator]).toInstance(idGenerator)
      bind(classOf[MailerAPI]).toInstance(mailer)
      bind(classOf[AccountManager]).toInstance(accounts)
      bind(classOf[OAuth2Flow]).toInstance(oAuth2Flow)
      bind(classOf[MovedPageLookup]).toInstance(relocator)
      bind(classOf[FileStorage]).toInstance(fileStorage)
      bind(classOf[HtmlPages]).toInstance(htmlPages)
    }
  })

  override def getControllerInstance[A](clazz: Class[A]) = {
    injector.getInstance(clazz)
  }

  import controllers.renderError
  import play.api.mvc.Results._
  import views.html.errors._

  /**
   * Annoyingly, to render multilingual error pages from the
   * global object we have to mix in all the request, session,
   * and language-changing mechanisms.
   */
  protected val defaultPreferences = new SessionPrefs

  /**
   * Extract a language from the user's preferences and put it in
   * the implicit scope.
   */
  implicit def request2lang(implicit request: RequestHeader): Lang =
    request.preferences.language match {
      case None => Lang.defaultLang
      case Some(lang) => Lang(lang)
    }

  override def onError(request: RequestHeader, ex: Throwable) = {
    implicit def req: RequestHeader = request

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
    implicit def req: RequestHeader = request
    immediate(NotFound(renderError("errors.pageNotFound", pageNotFound())))
  }
}
