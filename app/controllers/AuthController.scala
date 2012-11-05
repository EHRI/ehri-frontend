package controllers

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.libs.concurrent.execution.defaultContext
import models.UserProfile
import models.AccessibleEntity
import play.api.libs.json.JsString
import defines.EntityType

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
              val fakeProfile = Some(UserProfile(Some(-1L), user.profile_id, ""))
              rest.EntityDAO(EntityType.UserProfile, fakeProfile).get(user.profile_id).map { profileOrError =>
                profileOrError match {
                  case Right(profile) => f(Some(user.withProfile(UserProfile(profile))))(request)
                  case Left(err) => sys.error("Unable to fetch user profile: " + err)
                }
              }
            }
          }
          case None => f(userOption)(request)
        }
    }
  }
}