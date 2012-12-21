package controllers.base

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth
import play.api.libs.concurrent.execution.defaultContext
import models.UserProfile
import defines.EntityType
import models.UserProfileRepr
import models.Entity
import play.api.libs.json.JsValue
import play.api.libs.json.JsString
import models.{Entity}
import acl.ItemPermissionSet
import defines.PermissionType
import defines.ContentType

/*
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with Auth with Authorizer {

  /**
   * Action composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  def userProfileAction(f: Option[User] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {

            // FIXME: This is a DELIBERATE BACKDOOR
            val currentUser = request.getQueryString("asUser").map { name =>
              println("CURRENT USER: " + name)
              println("WARNING: Running with user override backdoor for testing on: ?as=name")
              name
            }.getOrElse(user.profile_id)

            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              val fakeProfile = UserProfileRepr(Entity.fromString(currentUser, EntityType.UserProfile))
              val profileRequest = rest.EntityDAO(EntityType.UserProfile, Some(fakeProfile)).get(currentUser)
              val permsRequest = rest.PermissionDAO(fakeProfile).get
              // These requests should execute in parallel...
              for {
                r1 <- profileRequest
                r2 <- permsRequest
              } yield {
                // Check nothing errored horribly...
                if (r1.isLeft) sys.error("Unable to fetch user profile: " + r1.left.get)
                if (r2.isLeft) sys.error("Unable to fetch user permissions: " + r2.left.get)

                // We're okay, so construct the complete profile.
                val u = user.withProfile(UserProfileRepr(r1.right.get)).withGlobalPermissions(r2.right.get)
                f(Some(u))(request)
              }
            }
          }
          case None => f(userOption)(request)
        }
    }
  }

  def itemPermissionAction(id: String)(f: Option[User] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {

            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              val fakeProfile = UserProfileRepr(Entity.fromString(user.profile_id, EntityType.UserProfile))
              val profileRequest = rest.EntityDAO(EntityType.UserProfile, Some(fakeProfile)).get(user.profile_id)
              val permsRequest = rest.PermissionDAO(fakeProfile).get
              val itemPermRequest = rest.PermissionDAO(fakeProfile).getItem(id)
              // These requests should execute in parallel...
              for { r1 <- profileRequest ; r2 <- permsRequest ; r3 <- itemPermRequest } yield {
                // Check nothing errored horribly...
                if (r1.isLeft) sys.error("Unable to fetch user profile: " + r1.left.get)
                if (r2.isLeft) sys.error("Unable to fetch user permissions: " + r2.left.get)
                if (r3.isLeft) sys.error("Unable to fetch user permissions for item: " + r3.left.get)
                // We're okay, so construct the complete profile.
                println("ITEM PERMS: " + r3.right.get)
                val u = user.withProfile(UserProfileRepr(r1.right.get))
                  .withGlobalPermissions(r2.right.get)
                  .withItemPermissions(r3.right.get)
                f(Some(u))(request)
              }
            }
          }
          case None => f(userOption)(request)
        }
    }
  }

    def withItemPermission(id: String,
      perm: PermissionType.Value)(
      f: Option[User] => Request[AnyContent] => Result)(
          implicit contentType: ContentType.Value): Action[AnyContent] = {
    itemPermissionAction(id) { implicit maybeUser => implicit request =>
      
      // If we have a user, and that user has a set of permissions, check them
      val resultOpt = for (user <- maybeUser ; iperms <- user.itemPermissions ; perms <- user.globalPermissions) yield {
        if (perms.has(contentType, perm) || iperms.has(perm) )
          f(maybeUser)(request)
        else
          Unauthorized(views.html.errors.permissionDenied())
      }
      
      resultOpt.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  } 

  def withContentPermission(
      perm: PermissionType.Value)(f: Option[User] => Request[AnyContent] => Result)(
          implicit contentType: ContentType.Value): Action[AnyContent] = {
    userProfileAction { implicit maybeUser => implicit request =>
      
      // If we have a user, and that user has a set of permissions, check them
      val resultOpt = for (user <- maybeUser ; perms <- user.globalPermissions) yield {
        if (perms.has(contentType, perm))
          f(maybeUser)(request)
        else
          Unauthorized(views.html.errors.permissionDenied())
      }
      
      resultOpt.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
    }
  } 

}