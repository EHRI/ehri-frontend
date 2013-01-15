package controllers.base

import _root_.models.UserProfile
import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth
import play.api.libs.concurrent.Execution.Implicits._
import defines.EntityType
import defines.PermissionType
import defines.ContentType

import models.UserProfile
import java.net.ConnectException
import rest.ServerError

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
              // TODO: For the permissions to be properly initialized they must
              // recieve a completely-constructed instance of the UserProfile
              // object, complete with the groups it belongs to. Since this isn't
              // available initially, and we don't want to block for it to become
              // available, we should probably add the user to the permissions when
              // we have both items from the server.
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
              } recover {
                case e: ConnectException => {
                  // We still have to show the user is logged in, so use the fake profile in the error view
                  val fakeUserProfile = fakeProfile.copy(account=Some(user))
                  InternalServerError(views.html.errors.serverTimeout()(Some(fakeUserProfile), request))
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
   *
   *  NB: Since we want to get the user's permissions in parallel with
   *  their global perms and user profile, we don't wrap userProfileAction
   *  but duplicate a bunch of code instead ;(
   */
  def itemPermissionAction(contentType: ContentType.Value, id: String)(f: Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {

            // FIXME: This is a DELIBERATE BACKDOOR
            val currentUser = USER_BACKDOOR__(user, request)

            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              // TODO: For the permissions to be properly initialized they must
              // recieve a completely-constructed instance of the UserProfile
              // object, complete with the groups it belongs to. Since this isn't
              // available initially, and we don't want to block for it to become
              // available, we should probably add the user to the permissions when
              // we have both items from the server.
              val fakeProfile = UserProfile(models.Entity.fromString(user.profile_id, EntityType.UserProfile))
              val getProf = rest.EntityDAO(
                EntityType.UserProfile, Some(fakeProfile)).get(currentUser)
              // NB: Instead of getting *just* global perms here we also fetch
              // everything in scope for the given item
              val getGlobalPerms = rest.PermissionDAO(fakeProfile).getScope(id)
              val getItemPerms = rest.PermissionDAO(fakeProfile).getItem(contentType, id)
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
              } recover {
                case e: ConnectException => {
                  // We still have to show the user is logged in, so use the fake profile in the error view
                  val fakeUserProfile = fakeProfile.copy(account=Some(user))
                  InternalServerError(views.html.errors.serverTimeout()(Some(fakeUserProfile), request))
                }
              }
            }
          }
          case None => f(None)(request)
        }
    }
  }

  /**
   * Wrap userProfileAction to ensure we have a user, or
   * access is denied
   */
  def withUserAction(f: UserProfile => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit  maybeUser => implicit request =>
      maybeUser.map { user =>
        f(user)(request)
      }.getOrElse(authenticationFailed(request))
    }
  }

  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  def adminAction(
    f: UserProfile => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit  maybeUser => implicit request =>
      maybeUser.flatMap { user =>
        if (user.isAdmin) Some(f(user)(request))
        else None
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }



  /**
   * Wrap itemPermissionAction to ensure a given permission is present,
   * and return an action with the user in scope.
   */
  def withItemPermission(id: String,
    perm: PermissionType.Value, contentType: ContentType.Value)(
      f: UserProfile => Request[AnyContent] => Result): Action[AnyContent] = {
    itemPermissionAction(contentType, id) { implicit maybeUser => implicit request =>
      maybeUser.flatMap { user =>
        if (user.hasPermission(contentType, perm)) Some(f(user)(request))
        else None
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }

  /**
   * Wrap userProfileAction to ensure a given *global* permission is present,
   * and return an action with the user in scope.
   */
  def withContentPermission(
    perm: PermissionType.Value, contentType: ContentType.Value)(
      f: UserProfile => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit maybeUser => implicit request =>
      maybeUser.flatMap { user =>
        if (user.hasPermission(contentType, perm)) Some(f(user)(request))
        else None
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  }
}