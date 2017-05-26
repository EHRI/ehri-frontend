package controllers.portal.base

import java.util.concurrent.TimeUnit

import auth.AccountManager
import auth.handler.AuthHandler
import play.api.{Configuration, Logger}
import defines.{EntityType, EventType}
import play.api.http.{ContentTypes, HeaderNames}
import play.api.i18n.MessagesApi
import utils._
import controllers.{AppComponents, renderError}
import models.UserProfile
import play.api.mvc._
import controllers.base.{ControllerHelpers, CoreActionBuilders, SessionPreferences}
import utils.caching.FutureCache

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}
import models.base.AnyModel
import models.view.UserDetails
import backend.{ApiUser, DataApi}
import global.GlobalConfig
import play.api.cache.SyncCacheApi
import play.api.mvc.Result
import utils.search.{SearchEngine, SearchItemResolver}
import views.MarkdownRenderer
import views.html.errors.{itemNotFound, maintenance}


trait PortalController
  extends CoreActionBuilders
  with ControllerHelpers
  with SessionPreferences[SessionPrefs] {

  private def logger = Logger(getClass)

  // Abstract controller components, injected into super classes
  def appComponents: AppComponents

  // Implicits hoisted to class scope so as to be provided to views
  protected implicit def cache: SyncCacheApi = appComponents.cacheApi
  protected implicit def globalConfig: GlobalConfig = appComponents.globalConfig
  protected implicit def markdown: MarkdownRenderer = appComponents.markdown

  protected def accounts: AccountManager = appComponents.accounts
  protected def dataApi: DataApi = appComponents.dataApi
  protected def config: Configuration = appComponents.config
  protected def authHandler: AuthHandler = appComponents.authHandler

  protected def searchEngine: SearchEngine = appComponents.searchEngine
  protected def searchResolver: SearchItemResolver = appComponents.searchResolver

  // By default, all controllers require auth unless ehri.portal.secured
  // is set to false in the config, which it is by default.
  override def staffOnly: Boolean = config.getOptional[Boolean]("ehri.portal.secured").getOrElse(true)
  override def verifiedOnly: Boolean = config.getOptional[Boolean]("ehri.portal.secured").getOrElse(true)

  /**
   * The user's default preferences. The `SessionPreferences` trait generates
   * a preferences object from a request object's cookie, falling back to this
   * if the cookie is invalid or doesn't exist. It will then generate an
   * **implicit** `preferences` object that can be picked up by views.
   */
  protected val defaultPreferences = new SessionPrefs

  /**
   * Ensure that functions requiring an optional user in scope
   * can retrieve it automatically from a user details object.
   */
  implicit def userFromDetails(implicit details: UserDetails): Option[UserProfile] = details.userOpt

  private def userWatchCacheKey(userId: String) = s"$userId-watchlist"

  protected def clearWatchedItemsCache(userId: String): Unit = cache.remove(userWatchCacheKey(userId))

  /**
   * Activity event types that we think the user would care about.
   */
  val activityEventTypes = List(
    EventType.deletion,
    EventType.creation,
    EventType.modification,
    EventType.modifyDependent,
    EventType.createDependent,
    EventType.deleteDependent,
    EventType.link,
    EventType.annotation,
    EventType.watch
  )

  /**
   * Activity item types that we think the user might care about.
   */
  val activityItemTypes = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.Country,
    EntityType.HistoricalAgent
  )

  /**
    * A redirect target after a successful user login.
    */
  override def loginSucceeded(request: RequestHeader): Future[Result] = {
    val uri = request.session.get(ACCESS_URI)
      .getOrElse(controllers.portal.users.routes.UserProfiles.profile().url)
    logger.debug(s"Redirecting logged-in user to: $uri")
    immediate(Redirect(uri).withSession(request.session - ACCESS_URI))
  }

  /**
    * A redirect target after a successful user logout.
    */
  override def logoutSucceeded(request: RequestHeader): Future[Result] =
    immediate(Redirect(controllers.portal.routes.Portal.index())
      .flashing("success" -> "logout.confirmation"))


  override def verifiedOnlyError(request: RequestHeader): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.verifiedOnly", views.html.errors.verifiedOnly())))
  }

  override def staffOnlyError(request: RequestHeader): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.staffOnly", views.html.errors.staffOnly())))
  }

  override def notFoundError(request: RequestHeader, msg: Option[String] = None): Future[Result] = {
    val doMoveCheck: Boolean = config.getOptional[Boolean]("ehri.handlePageMoved").getOrElse(false)
    implicit val r  = request
    val notFoundResponse = NotFound(renderError("errors.itemNotFound", itemNotFound(msg)))
    if (!doMoveCheck) immediate(notFoundResponse)
    else for {
      maybeMoved <- appComponents.pageRelocator.hasMovedTo(request.path)
    } yield maybeMoved match {
      case Some(path) => MovedPermanently(path)
      case None => notFoundResponse
    }
  }

  override def downForMaintenance(request: RequestHeader): Future[Result] = {
    implicit val r  = request
    immediate(ServiceUnavailable(renderError("errors.maintenance", maintenance())))
  }

  /**
   * A redirect target after a failed authentication.
   */
  override def authenticationFailed(request: RequestHeader): Future[Result] = {
    if (isAjax(request)) {
      logger.warn(s"Auth failed for: $request")
      immediate(Unauthorized("authentication failed"))
    } else {
      immediate(Redirect(controllers.portal.account.routes.Accounts.loginOrSignup())
        .withSession(ACCESS_URI -> request.uri))
    }
  }

  override def authorizationFailed(request: RequestHeader, user: UserProfile): Future[Result] = {
    implicit val r = request
    immediate(Forbidden(renderError("errors.permissionDenied", views.html.errors.permissionDenied())))
  }

  /**
   * Wrap some code generating an optional result, falling back to a 404.
   */
  def itemOr404(f: => Option[Result])(implicit request: RequestHeader): Result = {
    f.getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }

  /**
   * Wrap some code generating an optional result, falling back to a 404.
   */
  def futureItemOr404(f: => Option[Future[Result]])(implicit request: RequestHeader): Future[Result] = {
    f.getOrElse(immediate(NotFound(renderError("errors.itemNotFound", itemNotFound()))))
  }

  /**
   * Given an optional item and a function to produce a
   * result from it, run the function or fall back on a 404.
   */
  def itemOr404[T](item: Option[T])(f: => T => Result)(implicit request: RequestHeader): Result = {
    item.map(f).getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }

  /**
   * Fetched watched items for an optional user.
   */
  protected def watchedItemIds(implicit userIdOpt: Option[String]): Future[Seq[String]] = userIdOpt.map { userId =>
    FutureCache.getOrElse(userWatchCacheKey(userId), Duration.apply(20 * 60, TimeUnit.SECONDS)) {
      implicit val apiUser: ApiUser = ApiUser(Some(userId))
      userDataApi.watching[AnyModel](userId, PageParams.empty.withoutLimit).map { page =>
        page.items.map(_.id)
      }
    }
  }.getOrElse(Future.successful(Seq.empty))

  protected def exportXml(entityType: EntityType.Value, id: String, formats: Seq[String], asFile: Boolean = false)(
      implicit apiUser: ApiUser, request: RequestHeader): Future[Result] = {
    val format: String = request.getQueryString("format")
      .filter(formats.contains).getOrElse(formats.head)
    val params = request.queryString.filterKeys(_ == "lang")
    userDataApi.stream(s"classes/$entityType/$id/$format", params = params).map { sr =>
      val ct = sr.headers.headers.get(HeaderNames.CONTENT_TYPE)
        .flatMap(_.headOption).getOrElse(ContentTypes.XML)

      val disp = if (ct.contains("zip"))
        Seq("Content-Disposition" -> s"attachment; filename='$id-$format.zip'")
      else if (asFile)
        Seq("Content-Disposition" -> s"attachment; filename='$id-$format.xml'")
      else Seq.empty

      val heads: Map[String, String] = sr.headers.headers.map(s => (s._1, s._2.head))
      // If we're streaming a zip file, send it as an attachment
      // with a more useful filename...
      Status(sr.headers.status).chunked(sr.body).as(ct)
        .withHeaders(heads.toSeq ++ disp: _*)
    }
  }

  case class UserDetailsRequest[A](
    watched: Seq[String],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  /**
   * Action which fetches a user's profile and list of watched items.
   */
  protected def UserBrowseAction: ActionBuilder[UserDetailsRequest, AnyContent] = OptionalAccountAction andThen new CoreActionTransformer[OptionalAccountRequest,
    UserDetailsRequest] {
    override protected def transform[A](request: OptionalAccountRequest[A]): Future[UserDetailsRequest[A]] = {
      request.accountOpt.map { account =>
        implicit val apiUser: ApiUser = ApiUser(Some(account.id))
        val userF: Future[UserProfile] = userDataApi.get[UserProfile](account.id)
        val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(account.id))
        for {
          user <- userF
          userWithAccount = user.copy(account=Some(account))
          watched <- watchedF
        } yield UserDetailsRequest(watched, Some(userWithAccount), request)
      } getOrElse {
        immediate(UserDetailsRequest(Nil, None, request))
      }
    }
  }
}
