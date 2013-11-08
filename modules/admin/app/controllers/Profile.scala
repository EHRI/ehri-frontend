package controllers.admin

import controllers.base.EntityUpdate
import models.{UserProfileF, UserProfile}

import play.api._
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.i18n.Messages
import com.google.inject._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future.{successful => immediate}

/**
 * Controller for a user managing their own profile.
 * @param globalConfig
 */
@Singleton
class Profile @Inject()(implicit val globalConfig: global.GlobalConfig, val backend: rest.Backend) extends EntityUpdate[UserProfileF,UserProfile] {

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile
  val form = models.forms.UserProfileForm.form

  /**
   * Render a user's profile.
   * @return
   */
  def profile = withUserAction { implicit user => implicit request =>
    Ok(views.html.profile(user))
  }

  def updateProfile = withUserAction { implicit user => implicit request =>
    Ok(views.html.editProfile(
        user, form.fill(user.model), controllers.admin.routes.Profile.updateProfilePost))
  }

  def updateProfilePost = withUserAction.async { implicit user => implicit request =>
    // This action is more-or-less the same as in UserProfiles update, except
    // we don't allow the user to update their own identifier.
    val transform: UserProfileF => UserProfileF = { newDetails =>
      newDetails.copy(identifier = user.model.identifier)
    }

    updateAction(user, form, transform) { item => formOrItem => up => r =>
      formOrItem match {
        case Left(errorForm) =>
          BadRequest(views.html.editProfile(
            user, errorForm, controllers.admin.routes.Profile.updateProfilePost))
        case Right(item) => Redirect(controllers.admin.routes.Profile.profile)
          .flashing("success" -> Messages("confirmations.profileUpdated"))
      }
    }
  }
}
