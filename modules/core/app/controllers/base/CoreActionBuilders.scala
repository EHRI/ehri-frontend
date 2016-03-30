package controllers.base


import backend.{ApiUser, _}
import defines.{ContentTypes, PermissionType}
import jp.t2v.lab.play2.auth.AuthActionBuilders
import models.UserProfile
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Result, _}

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions


/**
 * Trait containing Action wrappers to handle different
 * types of site management and request authentication concerns
 */
trait CoreActionBuilders extends Controller with ControllerHelpers with AuthActionBuilders with AuthConfigImpl {

  /**
   * Inheriting controllers need to be provided/injected with
   * a dataApi implementation.
   */
  protected def dataApi: DataApi

  /**
   * Obtain a handle to the dataApi database in the context of
   * a particular user.
   *
   * @param apiUser the current user
   * @return a data api handle
   */
  protected def userDataApi(implicit apiUser: ApiUser): DataApiHandle =
    dataApi.withContext(apiUser)

  /**
   * Access the global configuration instance.
   */
  protected implicit def globalConfig: global.GlobalConfig

  /**
   * Indicates that the current controller is only accessible to
   * staff accounts.
   */
  protected def staffOnly = true

  /**
   * Indicates that the current controller is only accessible
   * to verified accounts.
   */
  protected def verifiedOnly = true

  /**
   * Indicates that the current controller is secured, which,
   * if set to false, overrides staffOnly and verifiedOnly.
   */
  protected lazy val secured = app
    .configuration.getBoolean("ehri.secured").getOrElse(true)

  /**
   * Abstract response methods that should be implemented by inheritors.
   */
  protected def verifiedOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  /**
   * This controller can only be accessed by staff
   */
  protected def staffOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  /**
   * A dataApi resource was not found
   */
  protected def notFoundError(request: RequestHeader, msg: Option[String] = None)(implicit context: ExecutionContext): Future[Result]

  /**
   * The user didn't have the required permissions
   */
  protected def authorizationFailed(request: RequestHeader, user: UserProfile)(implicit context: ExecutionContext): Future[Result]


  /**
   * The site is down currently for maintenance
   */
  protected def downForMaintenance(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  /**
   * Base trait for any type of request that contains
   * an optional user profile (which is most of them.)
   */
  protected trait WithOptionalUser { self: WrappedRequest[_] =>
    def userOpt: Option[UserProfile]
  }

  /**
   * Base trait for any type of request that contains
   * an authenticated user profile.
   */
  protected trait WithUser { self: WrappedRequest[_] =>
    def user: UserProfile
  }

  /**
   * A wrapped request that optionally contains a user's profile.
   * @param userOpt the optional profile
   * @param request the underlying request
   * @tparam A the type of underlying request
   */
  protected case class OptionalUserRequest[A](userOpt: Option[UserProfile], request: Request[A])
    extends WrappedRequest[A](request)
    with WithOptionalUser

  /**
   * A wrapped request that contains a user's profile.
   * @param user the profile
   * @param request the underlying request
   * @tparam A the type of underlying request
   */
  protected case class WithUserRequest[A](user: UserProfile, request: Request[A])
    extends WrappedRequest[A](request)
    with WithUser

  /**
   * Implicit helper to convert an in-scope optional profile to an `ApiUser` instance.
   *
   * @param userOpt an optional profile
   * @return an API user, which may be anonymous
   */
  protected implicit def userOpt2apiUser(implicit userOpt: Option[UserProfile]): ApiUser =
    ApiUser(userOpt.map(_.id))

  /**
   * Implicit helper to convert an in-scope profile to an `AuthenticatedUser` instance.
   *
   * @param user a user profile
   * @return an authenticated API user
   */
  protected implicit def user2apiUser(implicit user: UserProfile): AuthenticatedUser =
    AuthenticatedUser(user.id)

  /**
   * Implicit helper to transform an in-scope `OptionalProfileRequest` (of any type)
   * into an optional user profile. This is used by views that need an implicit user profile
   * but are only given an `OptionalProfileRequest`.
   * @param r an optional user request
   * @return an optional user profile
   */
  protected implicit def optionalUserRequest2UserOpt(implicit r: WithOptionalUser): Option[UserProfile] =
    r.userOpt

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
   * @param r the request with an authenticated user
   * @return an optional user profile
   */
  protected implicit def userRequest2userOpt(implicit r: WithUser): Option[UserProfile] =
    Some(r.user)

  /**
   * Transform an `OptionalAuthRequest` into an `OptionalProfileRequest` by
   * fetching the profile associated with the account and the user's global
   * permissions.
   */
  protected object FetchProfile extends ActionTransformer[OptionalAuthRequest, OptionalUserRequest] {
    def transform[A](request: OptionalAuthRequest[A]): Future[OptionalUserRequest[A]] = request.user.fold(
      ifEmpty = immediate(OptionalUserRequest[A](None, request))
    ) { account =>
      implicit val apiUser = AuthenticatedUser(account.id)
      val userF = userDataApi.get[UserProfile](UserProfile.UserProfileResource, account.id)
      val globalPermsF = userDataApi.globalPermissions(account.id)
      for {
        user <- userF
        globalPerms <- globalPermsF
        profile = user.copy(account = Some(account), globalPermissions = Some(globalPerms))
      } yield OptionalUserRequest[A](Some(profile), request)
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

  protected object EmbedTransformer extends ActionTransformer[OptionalAuthRequest,OptionalAuthRequest]{
    protected def transform[A](request: OptionalAuthRequest[A]): Future[OptionalAuthRequest[A]] = immediate {
      if (globalConfig.isEmbedMode(request)) new OptionalAuthRequest(None, request.underlying) else request
    }
  }

  /**
   * If the global read-only flag is enabled, remove the account from
   * the request, globally denying all secured actions.
   */
  protected object MaintenanceFilter extends ActionFilter[OptionalAuthRequest]{
    override protected def filter[A](request: OptionalAuthRequest[A]): Future[Option[Result]] = {
      if (globalConfig.maintenance) downForMaintenance(request).map(r => Some(r))
      else immediate(None)
    }
  }

  /**
   * If the IP WHITELIST file is present, check the incoming IP and show a 503 to
   * everyone else.
   */
  protected object IpFilter extends ActionFilter[OptionalAuthRequest]{
    override protected def filter[A](request: OptionalAuthRequest[A]): Future[Option[Result]] = {
      globalConfig.ipFilter.map { whitelist =>
        // Extract the client from the forwarded header, falling back
        // on the remote address. This is dependent on the proxy situation.
        if (whitelist.contains(remoteIp(request))) immediate(None)
        else downForMaintenance(request).map(r => Some(r))
      }.getOrElse(immediate(None))
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
   *  - the site is not in maintenance mode
   *  - they are allowed in this controller
   */
  protected def OptionalAccountAction =
    OptionalAuthAction andThen
      MaintenanceFilter andThen
      IpFilter andThen
      ReadOnlyTransformer andThen
      EmbedTransformer andThen
      AllowedFilter

  /**
   * Fetch the profile in addition to the account
   */
  protected def OptionalUserAction = OptionalAccountAction andThen FetchProfile

  /**
   * Ensure that a user is present
   */
  protected def WithUserAction = OptionalUserAction andThen new ActionRefiner[OptionalUserRequest, WithUserRequest] {
    protected def refine[A](request: OptionalUserRequest[A]) = {
      request.userOpt match {
        case None => authenticationFailed(request).map(r => Left(r))
        case Some(profile) => immediate(Right(WithUserRequest(profile, request)))
      }
    }
  }

  /**
   * Ensure that a user a given permission on a given content type
   * @param permissionType the permission type
   * @param contentType the content type
   */
  protected def WithContentPermissionAction(permissionType: PermissionType.Value, contentType: ContentTypes.Value) =
    WithUserAction andThen new ActionFilter[WithUserRequest] {
      override protected def filter[A](request: WithUserRequest[A]): Future[Option[Result]] = {
        if (request.user.hasPermission(contentType, permissionType)) immediate(None)
        else authorizationFailed(request, request.user).map(r => Some(r))
      }
    }

  /**
   * Check the user belongs to a given group.
   */
  protected def MustBelongTo(groupId: String) = WithUserAction andThen new ActionFilter[WithUserRequest] {
    protected def filter[A](request: WithUserRequest[A]): Future[Option[Result]] = {
      if (request.user.isAdmin || request.user.allGroups.exists(_.id == groupId)) immediate(None)
      else authorizationFailed(request, request.user).map(r => Some(r))
    }
  }

  /**
   * Check the user is an administrator to access this request
   */
  protected def AdminAction = WithUserAction andThen new ActionFilter[WithUserRequest] {
    protected def filter[A](request: WithUserRequest[A]): Future[Option[Result]] = {
      if (request.user.isAdmin) immediate(None)
      else authorizationFailed(request, request.user).map(r => Some(r))
    }
  }
}