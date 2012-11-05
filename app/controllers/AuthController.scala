package controllers

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.libs.concurrent.execution.defaultContext
import models.UserProfile
import models.AccessibleEntity
import play.api.libs.json.JsString
import defines.EntityType
import rest.PermissionDAO
import scala.concurrent.Future

/*
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with Auth with Authorizer {

  def userProfileAction(f: Option[User] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {
            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              val fakeProfile = UserProfile(None, user.profile_id, "")
              val profileRequest = rest.EntityDAO(EntityType.UserProfile, Some(fakeProfile)).get(user.profile_id)
              val permsRequest = rest.PermissionDAO(fakeProfile).get
              for {
                r1 <- profileRequest
                r2 <- permsRequest
              } yield {
                val p: User = r1 match {
                  case Right(profile) => user.withProfile(UserProfile(profile))
                  case Left(err) => sys.error("Unable to fetch user profile: " + err)
                }
                val p2: User = r2 match {
                  case Right(perms) => p.withPermissions(perms)
                  case Left(err) => sys.error("Unable to fetch user profile: " + err)
                }
                f(Some(p2))(request)
              }
            }
          }
          case None => f(userOption)(request)
        }
    }
  }
}