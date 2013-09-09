package controllers.base

import _root_.models.json.RestReadable
import models.{UserProfileF, UserProfile}
import play.api._
import play.api.mvc._
import play.api.i18n.Lang
import jp.t2v.lab.play20.auth.Auth
import play.api.libs.concurrent.Execution.Implicits._
import defines.EntityType
import defines.PermissionType
import defines.ContentTypes
import global.GlobalConfig

/**
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with ControllerHelpers with Auth with Authorizer {

  implicit val globalConfig: GlobalConfig

  /**
   * Provide functionality for changing the current locale.
   *
   * This is borrowed from:
   * https://github.com/julienrf/chooze/blob/master/app/controllers/CookieLang.scala
   */
  val localeForm = play.api.data.Form("locale" -> play.api.data.Forms.nonEmptyText)
  private val LANG = "lang"

  def changeLocale = Action { implicit request =>
    val referrer = request.headers.get(REFERER).getOrElse(globalConfig.routeRegistry.default.url)
    localeForm.bindFromRequest.fold(
      errors => {
        Logger.logger.debug("The locale can not be change to : " + errors.get)
        BadRequest(referrer)
      },
      locale => {
        Logger.logger.debug("Change user lang to : " + locale)
        Redirect(referrer).withCookies(Cookie(LANG, locale))
      })
  }

  override implicit def lang(implicit request: RequestHeader) = {
    request.cookies.get(LANG) match {
      case None => super.lang(request)
      case Some(cookie) => Lang(cookie.value)
    }
  }

  /**
   * WARNING: Remove this function (it's named funnily as a reminder.)
   * It provides a way to override the logged-in user's account and thus
   * do anything as anyone, provided they know the target user profile
   * id. Obviously, this is a big (albeit deliberate) security hole.
   */
  def USER_BACKDOOR__(account: models.sql.User, request: Request[AnyContent]): String = {
    if (request.method == "GET") {
      request.getQueryString("asUser").map { name =>
        println("CURRENT USER: " + name)
        println("WARNING: Running with user override backdoor for testing on: ?as=name")
        name
      }.getOrElse(account.profile_id)
    } else account.profile_id
  }
  
  /**
   * SystemEvent composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  def userProfileAction(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit maybeAccount => implicit request =>
      maybeAccount.map { account =>

        // FIXME: This is a DELIBERATE BACKDOOR
        val currentUser = USER_BACKDOOR__(account, request)
        val fakeProfile = UserProfile(UserProfileF(id=Some(account.profile_id), identifier="", name=""))
        implicit val maybeUser = Some(fakeProfile)

        AsyncRest {
          // TODO: For the permissions to be properly initialized they must
          // recieve a completely-constructed instance of the UserProfile
          // object, complete with the groups it belongs to. Since this isn't
          // available initially, and we don't want to block for it to become
          // available, we should probably add the account to the permissions when
          // we have both items from the server.
          val getProf = rest.EntityDAO[UserProfile](EntityType.UserProfile, maybeUser).get(currentUser)
          val getGlobalPerms = rest.PermissionDAO(maybeUser).get
          // These requests should execute in parallel...
          for { r1 <- getProf; r2 <- getGlobalPerms } yield {
            for { entity <- r1.right; gperms <- r2.right } yield {
              val up = entity.copy(account = Some(account), globalPermissions = Some(gperms))
              f(Some(up))(request)
            }
          }
        }
      }.getOrElse {
        f(None)(request)
      }
    }
  }

  /**
   * Given an item ID and a user, fetch:
   * 	- the user's profile
   *    - the user's global permissions
   *    - the item permissions for that user
   *
   *  NB: Since we want to get the user's permissions in parallel with
   *  their global perms and user profile, we don't wrap userProfileAction
   *  but duplicate a bunch of code instead ;(
   */
  def itemPermissionAction[MT](contentType: ContentTypes.Value, id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
        implicit rd: RestReadable[MT]): Action[AnyContent] = {
    optionalUserAction { implicit maybeAccount =>
      implicit request =>
      // FIXME: Shouldn't need to infer entity type from content type, they should be the same
      val entityType = EntityType.withName(contentType.toString)
      maybeAccount.map { account =>

        // FIXME: This is a DELIBERATE BACKDOOR
        val currentUser = USER_BACKDOOR__(account, request)

        val fakeProfile = UserProfile(UserProfileF(id=Some(account.profile_id), identifier="", name=""))
        implicit val maybeUser = Some(fakeProfile)

        AsyncRest {
          val getProf = rest.EntityDAO[UserProfile](
            EntityType.UserProfile, Some(fakeProfile)).get(currentUser)
          // NB: Instead of getting *just* global perms here we also fetch
          // everything in scope for the given item
          val getGlobalPerms = rest.PermissionDAO(maybeUser).getScope(id)
          val getItemPerms = rest.PermissionDAO(maybeUser).getItem(contentType, id)
          val getEntity = rest.EntityDAO[MT](entityType, maybeUser).get(id)
          // These requests should execute in parallel...
          for { r1 <- getProf; r2 <- getGlobalPerms; r3 <- getItemPerms ; r4 <- getEntity } yield {
            for { entity <- r1.right; gperms <- r2.right; iperms <- r3.right ; item <- r4.right } yield {
              val up = entity.copy(account = Some(account), globalPermissions = Some(gperms), itemPermissions = Some(iperms))
              f(item)(Some(up))(request)
            }
          }
        }
      } getOrElse {
        implicit val maybeUser  = None
        AsyncRest {
          rest.EntityDAO(entityType, None).get(id).map { itemOrErr =>
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