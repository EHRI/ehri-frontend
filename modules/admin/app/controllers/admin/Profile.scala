package controllers.admin

import models.{AccountDAO, UserProfileF, UserProfile}

import controllers.generic.Update
import defines.{ContentTypes, EntityType}
import play.api.i18n.Messages
import com.google.inject._
import backend.Backend

/**
 * Controller for a user managing their own profile.
 */
@Singleton
case class Profile @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Update[UserProfileF,UserProfile] {

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile
  val form = models.UserProfile.form

  /**
   * Render a user's profile and allow updating it.
   */
  def profile = withUserAction { implicit user => implicit request =>
    Ok(views.html.profile(user))
  }

  def updateProfile() = withUserAction { implicit user => implicit request =>
    Ok(views.html.editProfile(
        user, form.fill(user.model), controllers.admin.routes.Profile.updateProfilePost()))
  }

  def updateProfilePost() = withUserAction.async { implicit user => implicit request =>
    // This action is more-or-less the same as in UserProfiles update, except
    // we don't allow the user to update their own identifier.
    val transform: UserProfileF => UserProfileF = { newDetails =>
      newDetails.copy(identifier = user.model.identifier)
    }

    updateAction(user, form, transform) { item => formOrItem => up => r =>
      formOrItem match {
        case Left(errorForm) =>
          BadRequest(views.html.editProfile(
            user, errorForm, controllers.admin.routes.Profile.updateProfilePost()))
        case Right(u) => Redirect(controllers.admin.routes.Profile.profile())
          .flashing("success" -> Messages("confirmations.profileUpdated"))
      }
    }
  }
}
