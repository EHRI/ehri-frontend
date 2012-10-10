package controllers

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.{Auth,LoginLogout}
import play.api.libs.concurrent.execution.defaultContext

import models.EntityDAO
import models.UserProfile

/*
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with Auth with Authorizer {

  def userProfileAction(f: Option[User] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption => implicit request => 
      userOption match {
        case Some(user) => {
          Async {            
            EntityDAO("userProfile").get(user.profile_id).map { profileOrError =>
              profileOrError match {
                case Right(profile) => f(Some(user.withProfile(Some(UserProfile(profile)))))(request)
                case Left(err) => sys.error("Unable to fetch user profile!")
              }              
            }
          }
        }
        case None => f(userOption)(request)
      }
    }
  }
}