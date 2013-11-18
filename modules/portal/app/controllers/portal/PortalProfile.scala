package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{AuthController, ControllerHelpers}
import models.{UserProfile, UserProfileF}
import controllers.generic.Update
import play.api.i18n.Messages
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.libs.json.{Format, Json}
import utils.{PageParams, ListParams}
import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalProfile extends Update[UserProfileF,UserProfile] {
  self: Controller with ControllerHelpers with AuthController =>

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile
  val form = models.forms.UserProfileForm.form

  def profile = withUserAction.async { implicit user => implicit request =>
    val params = PageParams.fromRequest(request)
    for {
      watchList <- backend.pageWatching(user.id, params)
    } yield Ok(views.html.p.profile.profile(watchList))
  }

  def updateProfile = withUserAction { implicit user => implicit request =>
    if (isAjax) {
      Ok(views.html.p.profile.editProfileForm(
        form.fill(user.model), controllers.portal.routes.Portal.updateProfilePost))
    } else {
      Ok(views.html.p.profile.editProfile(
        form.fill(user.model), controllers.portal.routes.Portal.updateProfilePost))
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
                errorForm, controllers.portal.routes.Portal.updateProfilePost))
          }
        }
        case Right(item) => {
          Redirect(controllers.portal.routes.Portal.profile)
            .flashing("success" -> Messages("confirmations.profileUpdated"))
        }
      }
    }
  }
}
