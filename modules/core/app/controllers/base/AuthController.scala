package controllers.base


import jp.t2v.lab.play2.auth.{AsyncAuth, AuthActionBuilders}

import scala.concurrent.ExecutionContext
import models.{UserProfileF, UserProfile}

import play.api.i18n.Lang
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

import defines.PermissionType
import defines.ContentTypes
import backend._
import backend.ApiUser
import play.api.mvc.Result


/**
 * Trait containing composable Action wrappers to handle different
 * types of request authentication.
 * NB: None of the methods here actually reer
 */
trait AuthController extends Controller with ControllerHelpers with AsyncAuth with AuthConfigImpl with AuthActionBuilders {

  val backend: Backend

  implicit val globalConfig: global.GlobalConfig

  // Override this to allow non-staff to view a page
  val staffOnly = true
  // Override this to allow non-verified users to view a page
  val verifiedOnly = true

  // Turning secured off will override staffOnly
  lazy val secured = play.api.Play.current.configuration.getBoolean("ehri.secured").getOrElse(true)

  private val LANG = "lang"


  /**
   * Abstract response methods that should be implemented by inheritors.
   *
   * @param request the request heaader
   * @param context the execution context
   * @return an error response
   */
  def verifiedOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  /**
   * Abstract response methods that should be implemented by inheritors.
   *
   * @param request the request heaader
   * @param context the execution context
   * @return an error response
   */
  def staffOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  /**
   * Abstract response methods that should be implemented by inheritors.
   *
   * @param request the request heaader
   * @param context the execution context
   * @return an error response
   */
  def notFoundError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]


  override implicit def request2lang(implicit request: RequestHeader) = {
    request.cookies.get(LANG) match {
      case None => super.request2lang(request)
      case Some(cookie) => Lang(cookie.value)
    }
  }

  /**
   * A wrapped request that optionally contains a user's profile.
   * @param profileOpt the optional profile
   * @param request the underlying request
   * @tparam A the type of underlying request
   */
  case class OptionalProfileRequest[A](profileOpt: Option[UserProfile], request: Request[A]) extends WrappedRequest[A](request)

  /**
   * A wrapped request that contains a user's profile.
   * @param profile the profile
   * @param request the underlying request
   * @tparam A the type of underlying request
   */
  case class ProfileRequest[A](profile: UserProfile, request: Request[A]) extends WrappedRequest[A](request)

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
  protected implicit def optionalProfileRequest2profileOpt(implicit opr: OptionalProfileRequest[_]): Option[UserProfile] =
    opr.profileOpt

  /**
   * Implicit helper to transform an in-scope `ProfileRequest` (of any type)
   * into an optional (but full) user profile. Used by views that need a user profile but are only given
   * a `ProfileRequest`
   *
   * @param pr the profile request
   * @return an optional user profile
   */
  protected implicit def profileRequest2profileOpt(implicit pr: ProfileRequest[_]): Option[UserProfile] =
    Some(pr.profile)

  /**
   * Transform an `OptionalAuthRequest` into an `OptionalProfileRequest` by
   * fetching the profile associated with the account and the user's global
   * permissions.
   */
  protected object FetchProfile extends ActionTransformer[OptionalAuthRequest, OptionalProfileRequest] {
    def transform[A](request: OptionalAuthRequest[A]): Future[OptionalProfileRequest[A]] = request.user.fold(
      immediate(new OptionalProfileRequest[A](None, request))
    ) { account =>
      implicit val apiUser = ApiUser(Some(account.id))
      val userF = backend.get[UserProfile](UserProfile.Resource, account.id)
      val globalPermsF = backend.getGlobalPermissions(account.id)
      for {
        user <- userF
        globalPerms <- globalPermsF
        profile = user.copy(account = Some(account), globalPermissions = Some(globalPerms))
      } yield new OptionalProfileRequest[A](Some(profile), request)
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
   * Check the user is allowed in this controller based on their account's
   * `staff` and `verified` flags.
   */
  protected object AdminFilter extends ActionFilter[OptionalProfileRequest] {
    protected def filter[A](request: OptionalProfileRequest[A]): Future[Option[Result]] = {
      request.profileOpt.filter(!_.isAdmin)
        .map(_ => authenticationFailed(request).map(r => Some(r))).getOrElse(immediate(None))
    }
  }

  /**
   * Given an optional profile request, convert to a concrete profile request if the
   * profile is present, or return an authentication failed.
   */
  protected object WithUserRefiner extends ActionRefiner[OptionalProfileRequest, ProfileRequest] {
    protected def refine[A](request: OptionalProfileRequest[A]) = {
      request.profileOpt match {
        case None => authenticationFailed(request).map(r => Left(r))
        case Some(profile) => immediate(Right(ProfileRequest(profile, request)))
      }
    }
  }

  def OptionalProfileAction = OptionalAuthAction andThen ReadOnlyTransformer andThen AllowedFilter andThen FetchProfile

  def WithUserAction = OptionalProfileAction andThen WithUserRefiner

  def AdminAction = OptionalProfileAction andThen AdminFilter

  /**
   * SystemEvent composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  @scala.deprecated(message = "Use OptionalProfileAction instead", since = "1.0.2")
  object userProfileAction {
    def async[A](bodyParser: BodyParser[A])(f: Option[UserProfile] => Request[A] => Future[Result]): Action[A] = {

      OptionalAuthAction.async[A](bodyParser) { implicit authRequest =>

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
   * Given an item ID fetch the item.
   */
  object itemAction {
    def async[MT](resource: BackendResource[MT], id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(
        implicit rd: BackendReadable[MT]): Action[AnyContent] = {
      OptionalProfileAction.async { implicit request =>
        backend.get(resource, id).flatMap { item =>
          f(item)(request.profileOpt)(request)
        }
      }
    }

    def apply[MT](resource: BackendResource[MT], id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT]): Action[AnyContent] = {
      async(resource, id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
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
      OptionalProfileAction.async(bodyParser = bodyParser) { implicit request =>
        request.profileOpt.map { user =>

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
      OptionalProfileAction.async(bodyParser) { implicit request =>
        request.profileOpt.map { user =>
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
      OptionalProfileAction.async { implicit  request =>
        request.profileOpt.map { user =>
          if (user.isAdmin) f(request.profileOpt)(request)
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
  def secured(f: Option[UserProfile] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
    OptionalProfileAction.async { implicit  request =>
      if (request.profileOpt.isDefined) f(request.profileOpt)(request)
      else authenticationFailed(request)
    }
  }
}