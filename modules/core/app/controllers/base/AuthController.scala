package controllers.base

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import models.{UserProfileF, UserProfile}
import models.json.{RestResource, RestReadable}
import play.api.mvc._
import play.api.i18n.Lang
import jp.t2v.lab.play2.auth.AsyncAuth
import play.api.libs.concurrent.Execution.Implicits._
import utils.renderError
import defines.PermissionType
import defines.ContentTypes
import backend.{ApiUser, Backend}
import backend.rest.{ItemNotFound,PermissionDenied}
import views.html.errors.itemNotFound

/**
 * Trait containing composable Action wrappers to handle different
 * types of request authentication.
 */
trait AuthController extends Controller with ControllerHelpers with AsyncAuth with AuthConfigImpl {

  val backend: Backend

  implicit val globalConfig: global.GlobalConfig

  // Override this to allow non-staff to view a page
  val staffOnly = true
  // Override this to allow non-verified users to view a page
  val verifiedOnly = true

  // Turning secured off will override staffOnly
  lazy val secured = play.api.Play.current.configuration.getBoolean("ehri.secured").getOrElse(true)

  implicit def apiUser(implicit userOpt: Option[UserProfile]): ApiUser = ApiUser(userOpt.map(_.id))

  /**
   * Provide functionality for changing the current locale.
   *
   * This is borrowed from:
   * https://github.com/julienrf/chooze/blob/master/app/controllers/CookieLang.scala
   */
  private val LANG = "lang"

  def changeLocale(lang: String) = Action { implicit request =>
    val referrer = request.headers.get(REFERER).getOrElse(globalConfig.routeRegistry.default.url)
    Redirect(referrer).withCookies(Cookie(LANG, lang))
  }

  override implicit def request2lang(implicit request: RequestHeader) = {
    request.cookies.get(LANG) match {
      case None => super.request2lang(request)
      case Some(cookie) => Lang(cookie.value)
    }
  }

  /**
   * ActionBuilder that randles REST errors appropriately...
   */
  object RestAction extends ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      block(request) recoverWith {
        case e: PermissionDenied => Future.successful(play.api.mvc.Results.Unauthorized("denied! No stairway!"))
        case e: ItemNotFound => Future.successful(play.api.mvc.Results.NotFound("Not found! " + e.toString))
        case e: java.net.ConnectException => Future.successful(play.api.mvc.Results.NotFound("No database!"))
      }
    }
  }


  /**
   * SystemEvent composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  object userProfileAction {
    def async[A](bodyParser: BodyParser[A])(f: Option[UserProfile] => Request[A] => Future[Result]): Action[A] = {
      optionalUserAction.async[A](bodyParser) { implicit maybeAccount => implicit request =>
        maybeAccount.map { account =>
          if (staffOnly && secured && !account.staff) {
            immediate(Unauthorized(renderError("errors.staffOnly",
              views.html.errors.staffOnly())))
          } else if (verifiedOnly && secured && !account.verified) {
            immediate(Unauthorized(renderError("errors.verifiedOnly",
              views.html.errors.verifiedOnly())))
          } else {
            // For the permissions to be properly initialized they must
            // recieve a completely-constructed instance of the UserProfile
            // object, complete with the groups it belongs to. Since this isn't
            // available initially, and we don't want to block for it to become
            // available, we should probably add the account to the permissions when
            // we have both items from the server.
            val fakeProfile = UserProfile(UserProfileF(id=Some(account.id), identifier="", name=""))
            implicit val maybeUser = Some(fakeProfile)

            for {
              user <- backend.get[UserProfile](UserProfile.Resource, account.id)
              globalPerms <- backend.getGlobalPermissions(fakeProfile)
              up = user.copy(account = Some(account), globalPermissions = Some(globalPerms.copy(user=user)))
              r <- f(Some(up))(request)
            } yield r
          }
        } getOrElse {
          if ((staffOnly || verifiedOnly) && secured) {
            authenticationFailed(request)
          } else {
            f(None)(request)
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
    def async[MT](resource: RestResource[MT], id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(
        implicit rd: RestReadable[MT]): Action[AnyContent] = {
      userProfileAction.async { implicit userOpt => implicit request =>
        backend.get(resource, id).flatMap { item =>
          f(item)(userOpt)(request)
        }
      }
    }

    def apply[MT](resource: RestResource[MT], id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]): Action[AnyContent] = {
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
    def async[A,MT](bodyParser: BodyParser[A], contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[A] => Future[Result])(
        implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[A] = {
      userProfileAction.async(bodyParser = bodyParser) { implicit userOpt => implicit request =>
        userOpt.map { user =>

          // NB: We have to re-fetch the global perms here because they need to be
          // within the scope of the particular item. This could be optimised, but
          // it would involve some duplication of code.
          // These requests should execute in parallel...
          for {
            globalPerms <- backend.getScopePermissions(user, id)
            itemPerms <- backend.getItemPermissions(user, contentType, id)
            item <- backend.get(rs, id)
            up = user.copy(globalPermissions = Some(globalPerms), itemPermissions = Some(itemPerms))
            r <- f(item)(Some(up))(request)
          } yield r
        } getOrElse {
          backend.get(rs, id).flatMap { item =>
            f(item)(None)(request)
          }
        }
      }
    }

    def async[MT](contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(
      implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, contentType, id)(f)

    def apply[MT](contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
        implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[AnyContent] =
      async(contentType, id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))

    def apply[A, MT](bodyParser: BodyParser[A], contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, contentType, id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied
   */
  object withUserAction {
    def async[A](bodyParser: BodyParser[A])(f: UserProfile => Request[A] => Future[Result]): Action[A] = {
      userProfileAction.async(bodyParser) { implicit  maybeUser => implicit request =>
        maybeUser.map { user =>
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
  object adminAction {
    def async(f: Option[UserProfile] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      userProfileAction.async { implicit  maybeUser => implicit request =>
        maybeUser.flatMap { user =>
          if (user.isAdmin) Some(f(maybeUser)(request))
          else None
        } getOrElse {
          immediate(Unauthorized(views.html.errors.permissionDenied()))
        }
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
    def async[A,MT](bodyParser: BodyParser[A], id: String, perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
        f: MT => Option[UserProfile] => Request[A] => Future[Result])(implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[A] = {
      itemPermissionAction.async[A,MT](bodyParser, contentType, id) { entity => implicit maybeUser => implicit request =>
        maybeUser.flatMap { user =>
          if (user.hasPermission(permContentType.getOrElse(contentType), perm)) Some(f(entity)(maybeUser)(request))
          else None
        } getOrElse {
          immediate(Unauthorized(views.html.errors.permissionDenied()))
        }
      }
    }

    def async[MT](id: String, perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
        f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, id, perm, contentType, permContentType)(f)

    def apply[A,MT](bodyParser: BodyParser[A], id: String, perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
      f: MT => Option[UserProfile] => Request[A] => Result)(implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[A] =
      async(bodyParser, id, perm, contentType, permContentType)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))

    def apply[MT](id: String, perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
      f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rs: RestResource[MT], rd: RestReadable[MT]): Action[AnyContent] =
      async(BodyParsers.parse.anyContent, id, perm, contentType, permContentType)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
  }

  /**
   * Wrap userProfileAction to ensure a given *global* permission is present,
   * and return an action with the user in scope.
   */
  object withContentPermission {
    def async(perm: PermissionType.Value, contentType: ContentTypes.Value)(
        f: Option[UserProfile] => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      withUserAction.async { implicit user => implicit request =>
        if (user.hasPermission(contentType, perm)) {
          f(Some(user))(request)
        } else {
          immediate(Unauthorized(views.html.errors.permissionDenied()))
        }
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
    userProfileAction.async { implicit  maybeUser => implicit request =>
      if (maybeUser.isDefined) f(maybeUser)(request)
      else authenticationFailed(request)
    }
  }

  /**
   * Wrap some code generating an optional result, falling back to a 404.
   */
  def itemOr404(f: => Option[Result])(implicit request: RequestHeader): Result = {
    f.getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }

  /**
   * Given an optional item and a function to produce a
   * result from it, run the function or fall back on a 404.
   */
  def itemOr404[T](item: Option[T])(f: => T => Result)(implicit request: RequestHeader): Result = {
    item.map(f).getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }
}