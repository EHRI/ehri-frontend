package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models.{ProfileData, UserProfile, UserProfileF}
import play.api.i18n.Messages
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.libs.json.{JsObject, Json}
import utils.{SessionPrefs, PageParams}
import scala.concurrent.Future.{successful => immediate}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalProfile {
  self: Controller with ControllerHelpers with AuthController with SessionPreferences[SessionPrefs] =>

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile

  def prefs = Action { implicit request =>
    Ok(Json.toJson(preferences))
  }

  def updatePrefs() = Action { implicit request =>
    SessionPrefs.updateForm(request.preferences).bindFromRequest.fold(
      errors => BadRequest(errors.errorsAsJson),
      updated => {
        (if (isAjax) Ok(Json.toJson(updated))
        else Redirect(controllers.portal.routes.Portal.prefs()))
          .withPreferences(updated)
      }
    )
  }

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

  def updateProfile() = withUserAction { implicit user => implicit request =>
    val form = ProfileData.form.fill(ProfileData.fromUser(user))
    if (isAjax) {
      Ok(views.html.p.profile.editProfileForm(
        form, controllers.portal.routes.Portal.updateProfilePost()))
    } else {
      Ok(views.html.p.profile.editProfile(
        form, controllers.portal.routes.Portal.updateProfilePost()))
    }
  }

  def updateProfilePost() = withUserAction.async { implicit user => implicit request =>
    ProfileData.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(views.html.p.profile.editProfile(
        errForm, controllers.portal.routes.Portal.updateProfilePost()))),
      profile => backend.patch[UserProfile](user.id, Json.toJson(profile).as[JsObject]).map { userProfile =>
        Redirect(controllers.portal.routes.Portal.profile())
          .flashing("success" -> Messages("confirmations.profileUpdated"))
      }
    )
  }

  def deleteProfile() = withUserAction { implicit user => implicit request =>
    // Make sure the users knows where they're doing...
    ???
  }

  def deleteProfilePost() = withUserAction.async { implicit user => implicit request =>
    val anonymous = UserProfileF(id = Some(user.id),
      identifier = user.model.identifier, name = user.model.identifier)
    backend.update(user.id, anonymous).map { bool =>
      user.account.get.delete()
      ???
    }
  }
}
