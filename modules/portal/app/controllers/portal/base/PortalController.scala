package controllers.portal.base

import controllers.portal.Secured
import play.api.Logger
import defines.{EventType, EntityType}
import play.api.i18n.Lang
import utils._
import controllers.renderError
import models.UserProfile
import play.api.mvc._
import controllers.base.{SessionPreferences, ControllerHelpers, CoreActionBuilders}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}
import play.api.Play.current
import play.api.cache.Cache
import models.base.AnyModel
import caching.FutureCache
import models.view.UserDetails
import backend.ApiUser
import play.api.mvc.Result
import views.html.errors.{maintenance, itemNotFound}


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalController
  extends CoreActionBuilders
  with ControllerHelpers
  with PortalAuthConfigImpl
  with Secured
  with SessionPreferences[SessionPrefs] {

  def pageRelocator: MovedPageLookup

  /**
   * The user's default preferences. The `SessionPreferences` trait generates
   * a preferences object from a request object's cookie, falling back to this
   * if the cookie is invalid or doesn't exist. It will then generate an
   * **implicit** `preferences` object that can be picked up by views.
   */
  protected val defaultPreferences = new SessionPrefs

  /**
   * Extract a language from the user's preferences and put it in
   * the implicit scope.
   */
  override implicit def request2lang(implicit request: RequestHeader): Lang = {
    request.preferences.language match {
      case None => super.request2lang(request)
      case Some(lang) => Lang(lang)
    }
  }

  /**
   * Ensure that functions requiring an optional user in scope
   * can retrieve it automatically from a user details object.
   */
  implicit def userFromDetails(implicit details: UserDetails): Option[UserProfile] = details.userOpt

  private def userWatchCacheKey(userId: String) = s"$userId-watchlist"

  protected def clearWatchedItemsCache(userId: String) = Cache.remove(userWatchCacheKey(userId))

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
    EventType.annotation
  )

  /**
   * Activity item types tat we think the user might care about.
   */
  val activityItemTypes = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.Country,
    EntityType.HistoricalAgent,
    EntityType.Link,
    EntityType.Annotation
  )

  override def verifiedOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.verifiedOnly", views.html.errors.verifiedOnly())))
  }

  override def staffOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.staffOnly", views.html.errors.staffOnly())))
  }

  override def notFoundError(request: RequestHeader, msg: Option[String] = None)(implicit context: ExecutionContext): Future[Result] = {
    val doMoveCheck: Boolean = current.configuration.getBoolean("ehri.handlePageMoved").getOrElse(false)
    implicit val r  = request
    val notFoundResponse = NotFound(renderError("errors.itemNotFound", itemNotFound(msg)))
    if (!doMoveCheck) immediate(notFoundResponse)
    else for {
      maybeMoved <- pageRelocator.hasMovedTo(request.path)
    } yield maybeMoved match {
      case Some(path) => MovedPermanently(path)
      case None => notFoundResponse
    }
  }

  override def downForMaintenance(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(ServiceUnavailable(renderError("errors.maintenance", maintenance())))
  }

  /**
   * A redirect target after a failed authentication.
   */
  override def authenticationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    if (utils.isAjax(request)) {
      Logger.logger.warn("Auth failed for: {}", request.toString())
      immediate(Unauthorized("authentication failed"))
    } else {
      immediate(Redirect(controllers.portal.account.routes.Accounts.loginOrSignup())
        .withSession(ACCESS_URI -> request.uri))
    }
  }

  override def authorizationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
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
    import play.api.libs.concurrent.Execution.Implicits._
    FutureCache.getOrElse(userWatchCacheKey(userId), 20 * 60) {
      import play.api.libs.concurrent.Execution.Implicits._
      implicit val apiUser: ApiUser = ApiUser(Some(userId))
      backendHandle.watching[AnyModel](userId, PageParams.empty.withoutLimit).map { page =>
        page.items.map(_.id)
      }
    }
  }.getOrElse(Future.successful(Seq.empty))

  case class UserDetailsRequest[A](
    watched: Seq[String],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  /**
   * Action which fetches a user's profile and list of watched items.
   */
  protected def UserBrowseAction = OptionalAuthAction andThen new ActionTransformer[OptionalAuthRequest, UserDetailsRequest] {
    override protected def transform[A](request: OptionalAuthRequest[A]): Future[UserDetailsRequest[A]] = {
      request.user.map { account =>
        import play.api.libs.concurrent.Execution.Implicits._
        implicit val apiUser: ApiUser = ApiUser(Some(account.id))
        val userF: Future[UserProfile] = backendHandle.get[UserProfile](account.id)
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
