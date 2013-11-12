package controllers.portal

import controllers.base.{AuthController, ControllerHelpers}
import models.{Link, Annotation, UserProfile, UserProfileF}
import play.api._
import controllers.generic.Update
import play.api.i18n.Messages
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.libs.json.{Format, Json}
import backend.Page

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalProfile extends Update[UserProfileF,UserProfile] {
  self: Controller with ControllerHelpers with AuthController =>

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile
  val form = models.forms.UserProfileForm.form

  def profile = withUserAction { implicit user => implicit request =>

    // TODO: Pull this data from the backend...
    val follows = Page.empty[UserProfile]
    val links = Page.empty[Link]
    val annotations = Page.empty[Annotation]

    render {
      case Accepts.Json() =>
        Ok(Json.toJson(userOpt)(Format.optionWithNull(UserProfile.Converter.clientFormat)))
      case Accepts.Html() => {
        if (isAjax) {
          Ok(views.html.p.profile.profileDetails(user))
        } else {
          Ok(views.html.p.profile.profile(user, links, annotations, follows))
        }
      }
    }
  }

  def updateProfile = withUserAction { implicit user => implicit request =>
    if (isAjax) {
      Ok(views.html.p.profile.editProfileForm(
        user, form.fill(user.model), controllers.portal.routes.Portal.updateProfilePost))
    } else {
      Ok(views.html.p.profile.editProfile(
        user, form.fill(user.model), controllers.portal.routes.Portal.updateProfilePost))
    }
  }

  def updateProfilePost = withUserAction.async { implicit user => implicit request =>
    // This action is more-or-less the same as in UserProfiles update, except
    // we don't allow the user to update their own identifier.
    val transform: UserProfileF => UserProfileF = { newDetails =>
      newDetails.copy(identifier = user.model.identifier)
    }

    updateAction(user, form, transform) { item => formOrItem => up => r =>
      formOrItem match {
        case Left(errorForm) => {
          if (isAjax) {
            BadRequest(errorForm.errorsAsJson)
          } else {
            BadRequest(views.html.p.profile.editProfile(
                user, errorForm, controllers.portal.routes.Portal.updateProfilePost))
          }
        }
        case Right(item) => {
          if (isAjax) {
            Ok(views.html.p.profile.profileDetails(item))
          } else {
            Redirect(controllers.portal.routes.Portal.profile)
              .flashing("success" -> Messages("confirmations.profileUpdated"))
          }
        }
      }
    }
  }
}
