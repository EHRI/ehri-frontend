package controllers.base


import backend.{ApiUser, _}
import defines.{ContentTypes, PermissionType}
import jp.t2v.lab.play2.auth.AuthActionBuilders
import models.{UserProfile, UserProfileF}
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

  def notFoundError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

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
   * @param profile the profile
   * @param request the underlying request
   * @tparam A the type of underlying request
   */
  case class WithUserRequest[A](profile: UserProfile, request: Request[A]) extends WrappedRequest[A](request)

  /**
   * Implicit helper to convert an in-scope optional profile to an `ApiUser` instance.
   *
   * @param userOpt an optional profile
   * @return an API user, which may be anonymous
   */
  protected implicit def userOpt2apiUser(implicit userOpt: Option[UserProfile]): ApiUser = ApiUser(userOpt.map(_.id))

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
    Some(pr.profile)

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
  def OptionalUserAction = OptionalAuthAction andThen ReadOnlyTransformer andThen AllowedFilter andThen FetchProfile

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

  /**
   * SystemEvent composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  @scala.deprecated(message = "Use OptionalUserAction instead", since = "1.0.2")
  object userProfileAction {
    def async[A](bodyParser: BodyParser[A])(f: Option[UserProfile] => Request[A] => Future[Result]): Action[A] = {

      OptionalAuthAction.async[A](bodyParser) { authRequest =>

        authRequest.user.map { account =>
          if (staffOnly && secured && !account.staff) staffOnlyError(authRequest)
          else if (verifiedOnly && secured && !account.verified) verifiedOnlyError(authRequest)
          else if (globalConfig.readOnly) {

            // Return early if we're read-only...
            f(None)(authRequest)
          } else {
            // For the permissions to be properly initialized they must
            // receive a completely-constructed instance of the UserProfile
            // object, complete with the groups it belongs to. Since this isn't
            // available initially, and we don't want to block for it to become
            // available, we should probably add the account to the permissions when
            // we have both items from the server.
            val fakeProfile = UserProfile(UserProfileF(id=Some(account.id), identifier="", name=""))
            implicit val maybeUser = Some(fakeProfile)

            val userF = backend.get[UserProfile](UserProfile.Resource, account.id)
            val globalPermsF = backend.getGlobalPermissions(account.id)
            for {
              user <- userF
              globalPerms <- globalPermsF
              up = user.copy(account = Some(account), globalPermissions = Some(globalPerms))
              r <- f(Some(up))(authRequest)
            } yield r
          }
        } getOrElse {
          if ((staffOnly || verifiedOnly) && secured) {
            authenticationFailed(authRequest)
          } else {
            f(None)(authRequest)
          }
        }
      }
    }

    def async(f: Option[UserProfile] => Request[AnyContent] => Future[Result]): Action[AnyContent]
      = async(BodyParsers.parse.anyContent)(f)

    def apply[A](bodyParser: BodyParser[A])(f: Option[UserProfile] => Request[A] => Result): Action[A]
      = async(bodyParser)(f.andThen(_.andThen(t => Future.successful(t))))

    def apply(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent]
      = async(f.andThen(_.andThen(t => Future.successful(t))))
  }

  /**
   * Given an item ID and a user, fetch:
   * 	- the user's profile
   *    - the user's global permissions within that item's scope
   *    - the item permissions for that user
   */
  object itemPermissionAction {
    def async[A,MT](bodyParser: BodyParser[A], id: String)(f: MT => Option[UserProfile] => Request[A] => Future[Result])(
        implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[A] = {
      OptionalUserAction.async(bodyParser = bodyParser) { implicit request =>
        request.userOpt.map { user =>

          // NB: We have to re-fetch the global perms here because they need to be
          // within the scope of the particular item. This could be optimised, but
          // it would involve some duplication of code.
          // These requests should execute in parallel...
          val scopedPermsF = backend.getScopePermissions(user.id, id)
          val itemPermsF = backend.getItemPermissions(user.id, ct.contentType, id)
          val getF = backend.get(ct, id)
          for {
            globalPerms <- scopedPermsF
            itemPerms <- itemPermsF
            item <- getF
            up = user.copy(globalPermissions = Some(globalPerms), itemPermissions = Some(itemPerms))
            r <- f(item)(Some(up))(request)
          } yield r
        } getOrElse {
          backend.get(ct, id).flatMap { item =>
            f(item)(None)(request)
          }
        }
      }
    }

    def async[MT](id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, id)(f)

    def apply[MT](id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
        implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] =
      async(id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))

    def apply[A, MT](bodyParser: BodyParser[A], id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied
   */
  @scala.deprecated(message = "Use WithUserAction instead", since = "1.0.2")
  object withUserAction {
    def async[A](bodyParser: BodyParser[A])(f: UserProfile => Request[A] => Future[Result]): Action[A] = {
      OptionalUserAction.async(bodyParser) { implicit request =>
        request.userOpt.map { user =>
          f(user)(request)
        } getOrElse {
          authenticationFailed(request)
        }
      }
    }

    def async(f: UserProfile => Request[AnyContent] => Future[Result]): Action[AnyContent]
      = async(BodyParsers.parse.anyContent)(f)

    def apply[A](bodyParser: BodyParser[A])(f: UserProfile => Request[A] => Result): Action[A]
      = async(bodyParser)(f.andThen(_.andThen(t => immediate(t))))

    def apply(f: UserProfile => Request[AnyContent] => Result): Action[AnyContent]
      = apply(BodyParsers.parse.anyContent)(f)
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  @scala.deprecated(message = "Use AdminAction instead", since = "1.0.2")
  object adminAction {
    def async(f: Option[UserProfile] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      OptionalUserAction.async { implicit  request =>
        request.userOpt.map { user =>
          if (user.isAdmin) f(request.userOpt)(request)
          else authorizationFailed(request)
        } getOrElse authenticationFailed(request)
      }
    }

    def apply(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  object withItemPermission {
    def async[A,MT](bodyParser: BodyParser[A], id: String, perm: PermissionType.Value, permContentType: Option[ContentTypes.Value] = None)(
        f: MT => Option[UserProfile] => Request[A] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[A] = {
      itemPermissionAction.async[A,MT](bodyParser, id) {
          entity => implicit maybeUser => implicit request =>
        maybeUser.map { user =>
          if (user.hasPermission(permContentType.getOrElse(ct.contentType), perm)) f(entity)(maybeUser)(request)
          else authorizationFailed(request)
        } getOrElse authenticationFailed(request)
      }
    }

    def async[MT](id: String, perm: PermissionType.Value, permContentType: Option[ContentTypes.Value] = None)(
        f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, id, perm, permContentType)(f)

    def apply[A,MT](bodyParser: BodyParser[A], id: String, perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
      f: MT => Option[UserProfile] => Request[A] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[A] =
      async(bodyParser, id, perm, permContentType)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))

    def apply[MT](id: String, perm: PermissionType.Value, permContentType: Option[ContentTypes.Value] = None)(
      f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, id, perm, permContentType)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
  }

  /**
   * Wrap userProfileAction to ensure a given *global* permission is present,
   * and return an action with the user in scope.
   */
  object withContentPermission {
    def async(perm: PermissionType.Value, contentType: ContentTypes.Value)(
        f: Option[UserProfile] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      WithUserAction.async { implicit request =>
        if (request.profile.hasPermission(contentType, perm)) f(Some(request.profile))(request)
        else authorizationFailed(request)
      }
    }

    def apply(perm: PermissionType.Value, contentType: ContentTypes.Value)(
        f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
      async(perm, contentType)(f.andThen(_.andThen(t => Future.successful(t))))
    }
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied, but don't change the incoming parameters.
   */
  @deprecated(message = "Use WithUserAction instead", since = "1.0.2")
  def secured(f: Option[UserProfile] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
    OptionalUserAction.async { implicit  request =>
      if (request.userOpt.isDefined) f(request.userOpt)(request)
      else authenticationFailed(request)
    }
  }
}