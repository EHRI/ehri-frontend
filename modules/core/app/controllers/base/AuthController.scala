package controllers.base


import backend.{ApiUser, _}
import defines.{ContentTypes, PermissionType}
import jp.t2v.lab.play2.auth.AuthActionBuilders
import models.UserProfile
import play.api.i18n.Lang
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Result, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}
import scala.language.implicitConversions


/**
 * Trait containing composable Action wrappers to handle different
 * types of request authentication.
 * NB: None of the methods here actually reer
 */
trait AuthController extends Controller with ControllerHelpers with AuthActionBuilders with AuthConfigImpl {

  // Inheriting controllers need to be injected with
  // a backend implementation.
  val backend: Backend

  // NB: Implicit so it can be used as an implicit parameter in views
  // that are rendered from inheriting controllers.
  implicit val globalConfig: global.GlobalConfig

  // Override this to allow non-staff to view a page
  val staffOnly = true

  // Override this to allow non-verified users to view a page
  val verifiedOnly = true

  // Turning secured off will override staffOnly
  lazy val secured = play.api.Play.current.configuration.getBoolean("ehri.secured").getOrElse(true)

  /**
   * Abstract response methods that should be implemented by inheritors.
   */
  def verifiedOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  def staffOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  def notFoundError(request: RequestHeader, msg: Option[String] = None)(implicit context: ExecutionContext): Future[Result]

  def authorizationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  // If a lang cookie is present, use it...
  private val LANG = "lang"
  override implicit def request2lang(implicit request: RequestHeader): Lang = {
    request.cookies.get(LANG) match {
      case None => super.request2lang(request)
      case Some(cookie) => Lang(cookie.value)
    }
  }

  trait WithOptionalUser {
    self: WrappedRequest[_] =>

    def userOpt: Option[UserProfile]
  }

  /**
   * A wrapped request that optionally contains a user's profile.
   * @param userOpt the optional profile
   * @param request the underlying request
   * @tparam A the type of underlying request
   */
  case class OptionalUserRequest[A](userOpt: Option[UserProfile], request: Request[A])
    extends WrappedRequest[A](request)
    with WithOptionalUser

  /**
   * A wrapped request that contains a user's profile.
   * @param user the profile
   * @param request the underlying request
   * @tparam A the type of underlying request
   */
  case class WithUserRequest[A](user: UserProfile, request: Request[A]) extends WrappedRequest[A](request)

  /**
   * Implicit helper to convert an in-scope optional profile to an `ApiUser` instance.
   *
   * @param userOpt an optional profile
   * @return an API user, which may be anonymous
   */
  protected implicit def userOpt2apiUser(implicit userOpt: Option[UserProfile]): ApiUser =
    ApiUser(userOpt.map(_.id))

  /**
   * Implicit helper to transform an in-scope `OptionalProfileRequest` (of any type)
   * into an optional user profile. This is used by views that need an implicit user profile
   * but are only given an `OptionalProfileRequest`.
   * @param opr an optional profile request
   * @return an optional user profile
   */
  protected implicit def optionalUserRequest2UserOpt(implicit opr: WithOptionalUser): Option[UserProfile] =
    opr.userOpt

  /**
   * Implicit helper to transform an in-scope `OptionalAuthRequest` into an ApiUser.
   *
   * @param oar an optional auth request
   * @return an ApiUser, possibly anonymous
   */
  protected implicit def optionalAuthRequest2apiUser(implicit oar: OptionalAuthRequest[_]): ApiUser =
    ApiUser(oar.user.map(_.id))

  /**
   * Implicit helper to transform an in-scope `ProfileRequest` (of any type)
   * into an optional (but full) user profile. Used by views that need a user profile but are only given
   * a `ProfileRequest`
   *
   * @param pr the profile request
   * @return an optional user profile
   */
  protected implicit def profileRequest2profileOpt(implicit pr: WithUserRequest[_]): Option[UserProfile] =
    Some(pr.user)

  /**
   * Transform an `OptionalAuthRequest` into an `OptionalProfileRequest` by
   * fetching the profile associated with the account and the user's global
   * permissions.
   */
  protected object FetchProfile extends ActionTransformer[OptionalAuthRequest, OptionalUserRequest] {
    def transform[A](request: OptionalAuthRequest[A]): Future[OptionalUserRequest[A]] = request.user.fold(
      immediate(new OptionalUserRequest[A](None, request))
    ) { account =>
      implicit val apiUser = ApiUser(Some(account.id))
      val userF = backend.get[UserProfile](UserProfile.Resource, account.id)
      val globalPermsF = backend.getGlobalPermissions(account.id)
      for {
        user <- userF
        globalPerms <- globalPermsF
        profile = user.copy(account = Some(account), globalPermissions = Some(globalPerms))
      } yield new OptionalUserRequest[A](Some(profile), request)
    }
  }

  /**
   * If the global read-only flag is enabled, remove the account from
   * the request, globally denying all secured actions.
   */
  protected object ReadOnlyTransformer extends ActionTransformer[OptionalAuthRequest,OptionalAuthRequest]{
    protected def transform[A](request: OptionalAuthRequest[A]): Future[OptionalAuthRequest[A]] = immediate {
      if (globalConfig.readOnly) new OptionalAuthRequest(None, request.underlying) else request
    }
  }

  /**
   * If the global read-only flag is enabled, remove the account from
   * the request, globally denying all secured actions.
   */
  protected object MaintenanceFilter extends ActionFilter[OptionalAuthRequest]{
    override protected def filter[A](request: OptionalAuthRequest[A]): Future[Option[Result]] = immediate {
      if (globalConfig.maintenance) Some(ServiceUnavailable) else None
    }
  }

  /**
   * Check the user is allowed in this controller based on their account's
   * `staff` and `verified` flags.
   */
  protected object AllowedFilter extends ActionFilter[OptionalAuthRequest] {
    protected def filter[A](request: OptionalAuthRequest[A]): Future[Option[Result]] = {
      request.user.fold(
        if ((staffOnly || verifiedOnly) && secured) authenticationFailed(request).map(r => Some(r))
        else immediate(None)
      ) { account =>
        if (staffOnly && secured && !account.staff) staffOnlyError(request).map(r => Some(r))
        else if (verifiedOnly && secured && !account.verified) verifiedOnlyError(request).map(r => Some(r))
        else immediate(None)
      }
    }
  }

  /**
   * Fetch, if available, the user's profile, ensuring that:
   *  - the site is not read-only
   *  - they are allowed in this controller
   */
  def OptionalUserAction = OptionalAuthAction andThen MaintenanceFilter andThen ReadOnlyTransformer andThen AllowedFilter andThen FetchProfile

  /**
   * Ensure that a user a given permission on a given content type
   * @param permissionType the permission type
   * @param contentType the content type
   */
  def WithContentPermissionAction(permissionType: PermissionType.Value, contentType: ContentTypes.Value) =
    OptionalUserAction andThen new ActionFilter[OptionalUserRequest] {
      override protected def filter[A](request: OptionalUserRequest[A]): Future[Option[Result]] = {
        if (request.userOpt.exists(_.hasPermission(contentType, permissionType)))  Future.successful(None)
        else authenticationFailed(request).map(r => Some(r))
      }
    }

  /**
   * Ensure that a user is present
   */
  def WithUserAction = OptionalUserAction andThen new ActionRefiner[OptionalUserRequest, WithUserRequest] {
    protected def refine[A](request: OptionalUserRequest[A]) = {
      request.userOpt match {
        case None => authenticationFailed(request).map(r => Left(r))
        case Some(profile) => immediate(Right(WithUserRequest(profile, request)))
      }
    }
  }

  /**
   * Check the user is an administrator to access this request
   */
  def AdminAction = OptionalUserAction andThen new ActionFilter[OptionalUserRequest] {
    protected def filter[A](request: OptionalUserRequest[A]): Future[Option[Result]] = {
      request.userOpt.filter(!_.isAdmin)
        .map(_ => authenticationFailed(request).map(r => Some(r))).getOrElse(immediate(None))
    }
  }
}