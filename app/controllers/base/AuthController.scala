package controllers.base

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth
import play.api.libs.concurrent.execution.defaultContext
import defines.EntityType
import defines.PermissionType
import defines.ContentType

import models.UserProfile

/**
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with Auth with Authorizer {

  /**
   * WARNING: Remove this function (it's named funnily as a reminder.)
   * It provides a way to override the logged-in user's account and thus
   * do anything as anyone, provided they know the target user profile
   * id. Obviously, this is a big (albeit deliberate) security hole.
   */
  def USER_BACKDOOR__(user: models.sql.User, request: Request[AnyContent]): String = {
    request.getQueryString("asUser").map { name =>
      println("CURRENT USER: " + name)
      println("WARNING: Running with user override backdoor for testing on: ?as=name")
      name
    }.getOrElse(user.profile_id)
  }
  
  /**
   * ActionLog composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  def userProfileAction(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {

            // FIXME: This is a DELIBERATE BACKDOOR
            val currentUser = USER_BACKDOOR__(user, request)

            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              val fakeProfile = UserProfile(models.Entity.fromString(currentUser, EntityType.UserProfile))
              val getProf = rest.EntityDAO(EntityType.UserProfile, Some(fakeProfile)).get(currentUser)
              val getGlobalPerms = rest.PermissionDAO(fakeProfile).get
              // These requests should execute in parallel...
              val futureUserOrError = for { r1 <- getProf; r2 <- getGlobalPerms } yield {
                for { entity <- r1.right; gperms <- r2.right } yield {
                  UserProfile(entity, account = Some(user), globalPermissions = Some(gperms))
                }
              }

              futureUserOrError.map { userOrError =>
                userOrError match {
                  case Left(err) => sys.error("Unable to fetch page prerequisites: " + err)
                  case Right(up) => f(Some(up))(request)
                }
              }
            }
          }
          case None => f(None)(request)
        }
    }
  }

  /**
   * Given an item ID and a user, fetch:
   * 	- the user's profile
   *    - the user's global permissions
   *    - the item permissions for that user
   */
  def itemPermissionAction(id: String)(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {

            // FIXME: This is a DELIBERATE BACKDOOR
            val currentUser = USER_BACKDOOR__(user, request)

            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              val fakeProfile = UserProfile(
                models.Entity.fromString(user.profile_id, EntityType.UserProfile))

              val getProf = rest.EntityDAO(
                EntityType.UserProfile, Some(fakeProfile)).get(currentUser)
              val getGlobalPerms = rest.PermissionDAO(fakeProfile).get
              val getItemPerms = rest.PermissionDAO(fakeProfile).getItem(id)
              // These requests should execute in parallel...
              val futureUserOrError = for { r1 <- getProf; r2 <- getGlobalPerms; r3 <- getItemPerms } yield {
                for { entity <- r1.right; gperms <- r2.right; iperms <- r3.right } yield {
                  UserProfile(entity, account = Some(user),
                    globalPermissions = Some(gperms), itemPermissions = Some(iperms))
                }
              }

              futureUserOrError.map { userOrError =>
                userOrError match {
                  case Left(err) => sys.error("Unable to fetch page prerequisites: " + err)
                  case Right(up) => f(Some(up))(request)
                }
              }
            }
          }
          case None => f(None)(request)
        }
    }
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  def withItemPermission(id: String,
    perm: PermissionType.Value)(
      f: UserProfile => Request[AnyContent] => Result)(
        implicit contentType: ContentType.Value): Action[AnyContent] = {
    itemPermissionAction(id) { implicit maybeUser =>
      implicit request =>
        maybeUser.flatMap { user =>
          if (user.hasPermission(perm)) Some(f(user)(request))
          else None
        }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }

  /**
   * Wrap userProfileAction to ensure a given *global* permission is present,
   * and return an action with the user in scope.
   */
  def withContentPermission(
    perm: PermissionType.Value)(f: UserProfile => Request[AnyContent] => Result)(
      implicit contentType: ContentType.Value): Action[AnyContent] = {
    userProfileAction { implicit maybeUser =>
      implicit request =>
        maybeUser.flatMap { user =>
          if (user.hasPermission(perm)) Some(f(user)(request))
          else None
        }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }
}