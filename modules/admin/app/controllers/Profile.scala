package controllers.admin

import _root_.controllers.base.{EntityUpdate, EntitySearch}
import _root_.models.{UserProfileF, UserProfile, Isaar, IsadG}
import models.base.AnyModel

import play.api._
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.Json
import utils.search._
import solr.facet.FieldFacetClass

import com.google.inject._
import play.api.http.MimeTypes


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

  def updateProfilePost = withUserAction { implicit user => implicit request =>
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
