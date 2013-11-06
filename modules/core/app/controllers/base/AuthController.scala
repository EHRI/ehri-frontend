package controllers.base

import _root_.models.json.{RestResource, RestReadable}
import models.{UserProfileF, UserProfile}
import play.api._
import play.api.mvc._
import play.api.i18n.Lang
import jp.t2v.lab.play2.auth.Auth
import play.api.libs.concurrent.Execution.Implicits._
import defines.EntityType
import defines.PermissionType
import defines.ContentTypes
import global.GlobalConfig
import rest.ApiUser

/**
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with ControllerHelpers with Auth with Authorizer {

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
   * SystemEvent composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  def userProfileAction(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit maybeAccount => implicit request =>
      maybeAccount.map { account =>
        if (staffOnly && secured && !account.staff) {
          Unauthorized(views.html.errors.staffOnly())
        } else {
          val fakeProfile = UserProfile(UserProfileF(id=Some(account.id), identifier="", name=""))
          implicit val userOpt = Some(fakeProfile)

          AsyncRest {
            // TODO: For the permissions to be properly initialized they must
            // recieve a completely-constructed instance of the UserProfile
            // object, complete with the groups it belongs to. Since this isn't
            // available initially, and we don't want to block for it to become
            // available, we should probably add the account to the permissions when
            // we have both items from the server.
            val getProf = rest.EntityDAO().get[UserProfile](EntityType.UserProfile, account.id)
            val getGlobalPerms = rest.PermissionDAO().get(fakeProfile)
            // These requests should execute in parallel...
            for { r1 <- getProf; r2 <- getGlobalPerms } yield {
              for { entity <- r1.right; gperms <- r2.right } yield {
                val up = entity.copy(account = Some(account), globalPermissions = Some(gperms))
                f(Some(up))(request)
              }
            }
          }
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

  /**
   * Given an item ID fetch the item.
   */
  def itemAction[MT](entityType: EntityType.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
    implicit rd: RestReadable[MT]): Action[AnyContent] = {
    userProfileAction { implicit userOpt => implicit request =>
      userOpt.map { user =>
        AsyncRest {
          val getEntity = rest.EntityDAO().get(entityType, id)
          for { entity <- getEntity } yield {
            for { item <- entity.right } yield {
              f(item)(Some(user))(request)
            }
          }
        }
      } getOrElse {
        AsyncRest {
          rest.EntityDAO().get(entityType, id).map { itemOrErr =>
            itemOrErr.right.map { item =>
              f(item)(None)(request)
            }
          }
        }
      }
    }
  }

  /**
   * Given an item ID and a user, fetch:
   * 	- the user's profile
   *    - the user's global permissions within that item's scope
   *    - the item permissions for that user
   */
  def itemPermissionAction[MT](contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
        implicit rd: RestReadable[MT]): Action[AnyContent] = {
    userProfileAction { implicit userOpt => implicit request =>
      val entityType = EntityType.withName(contentType.toString)
      userOpt.map { user =>

        AsyncRest {
          // NB: We have to re-fetch the global perms here because they need to be
          // within the scope of the particular item. This could be optimised, but
          // it would involve some duplication of code.
          val getGlobalPerms = rest.PermissionDAO().getScope(user, id)
          val getItemPerms = rest.PermissionDAO().getItem(user, contentType, id)
          val getEntity = rest.EntityDAO().get(entityType, id)
          // These requests should execute in parallel...
          for { gp <- getGlobalPerms; ip <- getItemPerms ; entity <- getEntity } yield {
            for { gperms <- gp.right; iperms <- ip.right ; item <- entity.right } yield {
              val up = user.copy(globalPermissions = Some(gperms), itemPermissions = Some(iperms))
              f(item)(Some(up))(request)
            }
          }
        }
      } getOrElse {
        AsyncRest {
          rest.EntityDAO().get(entityType, id).map { itemOrErr =>
            itemOrErr.right.map { item =>
              f(item)(None)(request)
            }
          }
        }
      }
    }
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied
   */
  def withUserAction(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit  maybeUser => implicit request =>
      maybeUser.map { user =>
        f(maybeUser)(request)
      }.getOrElse(authenticationFailed(request))
    }
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  def adminAction(
    f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit  maybeUser => implicit request =>
      maybeUser.flatMap { user =>
        if (user.isAdmin) Some(f(maybeUser)(request))
        else None
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  def withItemPermission[MT](id: String,
    perm: PermissionType.Value, contentType: ContentTypes.Value, permContentType: Option[ContentTypes.Value] = None)(
      f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]): Action[AnyContent] = {
    itemPermissionAction[MT](contentType, id) { entity => implicit maybeUser => implicit request =>
      maybeUser.flatMap { user =>
        if (user.hasPermission(permContentType.getOrElse(contentType), perm)) Some(f(entity)(maybeUser)(request))
        else None
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }

  /**
   * Wrap userProfileAction to ensure a given *global* permission is present,
   * and return an action with the user in scope.
   */
  def withContentPermission(
    perm: PermissionType.Value, contentType: ContentTypes.Value)(
      f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit maybeUser => implicit request =>
      maybeUser.flatMap { user =>
        if (user.hasPermission(contentType, perm)) Some(f(maybeUser)(request))
        else None
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied, but don't change the incoming parameters.
   */
  def secured(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit  maybeUser => implicit request =>
      if (maybeUser.isDefined) f(maybeUser)(request)
      else authenticationFailed(request)
    }
  }
}