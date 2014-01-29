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
    val watchParams = PageParams.fromRequest(request, namespace = "watch")
    val linkParams = PageParams.fromRequest(request, namespace = "link")
    val annParams = PageParams.fromRequest(request, namespace = "ann")
  
    for {
      watchList <- backend.pageWatching(user.id, watchParams)
      links <- backend.userLinks(user.id, linkParams)
      anns <- backend.userAnnotations(user.id, annParams)
    } yield Ok(views.html.p.profile.profile(watchList, anns, links))
  }

  def watching = withUserAction.async { implicit user => implicit request =>
    val watchParams = PageParams.fromRequest(request)
    backend.pageWatching(user.id, watchParams).map { watchList =>
      Ok(views.html.p.profile.watchedItems(watchList))
    }
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

  def deleteProfile = withUserAction { implicit user => implicit request =>
    // Make sure the users knows where they're doing...
    ???
  }

  def deleteProfilePost = withUserAction.async { implicit user => implicit request =>
    val anonymous = UserProfileF(id = Some(user.id),
      identifier = user.model.identifier, name = user.model.identifier)
    backend.update(user.id, anonymous).map { bool =>
      user.account.get.delete()
      ???
    }
  }
}
