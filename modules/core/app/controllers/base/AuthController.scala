package controllers.base

import _root_.models.json.{RestResource, RestReadable}
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import models.{UserProfileF, UserProfile}
import models.json.RestReadable
import play.api.mvc._
import play.api.i18n.Lang
import jp.t2v.lab.play2.auth.AsyncAuth
import play.api.libs.concurrent.Execution.Implicits._
import defines.EntityType
import defines.PermissionType
import defines.ContentTypes
import global.GlobalConfig
import rest.{Backend, ApiUser}

/**
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with ControllerHelpers with AsyncAuth with Authorizer {

  val backend: Backend

  implicit val globalConfig: GlobalConfig

  // Override this to allow non-staff to view a page
  val staffOnly = true

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

  override implicit def lang(implicit request: RequestHeader) = {
    request.cookies.get(LANG) match {
      case None => super.lang(request)
      case Some(cookie) => Lang(cookie.value)
    }
  }

  /**
   * ActionBuilder that randles REST errors appropriately...
   */
  object RestAction extends ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[SimpleResult]) = {
      block(request) recoverWith {
        case e: rest.PermissionDenied => Future.successful(play.api.mvc.Results.Unauthorized("denied! No stairway!"))
        case e: rest.ItemNotFound => Future.successful(play.api.mvc.Results.NotFound("Not found! " + e.toString))
        case e: java.net.ConnectException => Future.successful(play.api.mvc.Results.NotFound("No database!"))
      }
    }
  }


  /**
   * SystemEvent composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  object userProfileAction {
    def async(f: Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      optionalUserAction.async[AnyContent](BodyParsers.parse.anyContent) { implicit maybeAccount => implicit request =>
        maybeAccount.map { account =>
          if (staffOnly && secured && !account.staff) {
            immediate(Unauthorized(views.html.errors.staffOnly()))
          } else {
            val fakeProfile = UserProfile(UserProfileF(id=Some(account.id), identifier="", name=""))
            implicit val maybeUser = Some(fakeProfile)

            // For the permissions to be properly initialized they must
            // recieve a completely-constructed instance of the UserProfile
            // object, complete with the groups it belongs to. Since this isn't
            // available initially, and we don't want to block for it to become
            // available, we should probably add the account to the permissions when
            // we have both items from the server.
            val getProf = backend.get[UserProfile](EntityType.UserProfile, account.id)
            val getGlobalPerms = backend.getGlobalPermissions(fakeProfile)
            // These requests should execute in parallel...
            for {
              entity <- getProf
              gperms <- getGlobalPerms
              up = entity.copy(account = Some(account), globalPermissions = Some(gperms))
              r <- f(Some(up))(request)
            } yield r
          }
        } getOrElse {
          if (staffOnly && secured) {
            authenticationFailed(request)
          } else {
            f(None)(request)
          }
        }
      }
    }

    def apply(f: Option[UserProfile] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => Future.successful(t))))
    }
  }

  /**
   * Given an item ID fetch the item.
   */
  object itemAction {
    def async[MT](entityType: EntityType.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
        implicit rd: RestReadable[MT]): Action[AnyContent] = {
      userProfileAction.async { implicit userOpt => implicit request =>
        backend.get(entityType, id).flatMap { item =>
          f(item)(userOpt)(request)
        }
      }
    }

    def apply[MT](entityType: EntityType.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT]): Action[AnyContent] = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }

  /**
   * Given an item ID and a user, fetch:
   * 	- the user's profile
   *    - the user's global permissions within that item's scope
   *    - the item permissions for that user
   */
  object itemPermissionAction {
    def async[MT](contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
        implicit rd: RestReadable[MT]): Action[AnyContent] = {
      userProfileAction.async { implicit userOpt => implicit request =>
        val entityType = EntityType.withName(contentType.toString)
        userOpt.map { user =>

          // NB: We have to re-fetch the global perms here because they need to be
          // within the scope of the particular item. This could be optimised, but
          // it would involve some duplication of code.
          val getGlobalPerms = backend.getScopePermissions(user, id)
          val getItemPerms = backend.getItemPermissions(user, contentType, id)
          val getEntity = backend.get(entityType, id)
          // These requests should execute in parallel...
          for {
            gperms <- getGlobalPerms
            iperms <- getItemPerms
            item <- getEntity
            up = user.copy(globalPermissions = Some(gperms), itemPermissions = Some(iperms))
            r <- f(item)(Some(up))(request)
          } yield r
        } getOrElse {
          backend.get(entityType, id).flatMap { item =>
            f(item)(None)(request)
          }
        }
      }
    }

    def apply[MT](contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
        implicit rd: RestReadable[MT]): Action[AnyContent] = {
      async(contentType, id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied
   */
  object withUserAction {
    def async(f: UserProfile => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      userProfileAction.async { implicit  maybeUser => implicit request =>
        maybeUser.map { user =>
          f(user)(request)
        } getOrElse {
          authenticationFailed(request)
        }
      }
    }

    def apply(f: UserProfile => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  object adminAction {
    def async(f: Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      userProfileAction.async { implicit  maybeUser => implicit request =>
        maybeUser.flatMap { user =>
          if (user.isAdmin) Some(f(maybeUser)(request))
          else None
        } getOrElse {
          immediate(Unauthorized(views.html.errors.permissionDenied()))
        }
      }
    }

    def apply(f: Option[UserProfile] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => immediate(t))))
    }
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  object withItemPermission {
    def async[MT](id: String, perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
        f: MT => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(implicit rd: RestReadable[MT]): Action[AnyContent] = {
      itemPermissionAction.async[MT](contentType, id) { entity => implicit maybeUser => implicit request =>
        maybeUser.flatMap { user =>
          if (user.hasPermission(permContentType.getOrElse(contentType), perm)) Some(f(entity)(maybeUser)(request))
          else None
        } getOrElse {
          immediate(Unauthorized(views.html.errors.permissionDenied()))
        }
      }
    }

    def apply[MT](id: String, perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
        f: MT => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]): Action[AnyContent] = {
      async(id, perm, contentType, permContentType)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }

  /**
   * Wrap userProfileAction to ensure a given *global* permission is present,
   * and return an action with the user in scope.
   */
  object withContentPermission {
    def async(perm: PermissionType.Value, contentType: ContentTypes.Value)(
        f: Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      userProfileAction.async { implicit maybeUser => implicit request =>
        maybeUser.flatMap { user =>
          if (user.hasPermission(contentType, perm)) Some(f(maybeUser)(request))
          else None
        } getOrElse {
          immediate(Unauthorized(views.html.errors.permissionDenied()))
        }
      }
    }

    def apply(perm: PermissionType.Value, contentType: ContentTypes.Value)(
        f: Option[UserProfile] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(perm, contentType)(f.andThen(_.andThen(t => Future.successful(t))))
    }
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied, but don't change the incoming parameters.
   */
  def secured(f: Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
    userProfileAction.async { implicit  maybeUser => implicit request =>
      if (maybeUser.isDefined) f(maybeUser)(request)
      else authenticationFailed(request)
    }
  }
}