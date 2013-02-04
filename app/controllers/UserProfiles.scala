package controllers

import _root_.models.forms.{UserProfileF, VisibilityForm}
import models.UserProfile
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import base._
import models.{Group, UserProfile}
import models.forms.{UserProfileF,VisibilityForm}
import scala.Some


object UserProfiles extends PermissionHolderController[UserProfile]
	with CRUD[UserProfileF,UserProfile] {



  val entityType = EntityType.UserProfile
  val contentType = ContentType.UserProfile

  val form = models.forms.UserProfileForm.form

  // NB: Because the UserProfile class has more optional
  // parameters we use the companion object apply method here.
  val builder = UserProfile.apply _

  def get(id: String) = getAction(id) { item => annotations =>
    implicit userOptOpt =>
      implicit request =>
        Ok(views.html.userProfile.show(UserProfile(item), annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(UserProfile(item), page, ListParams()))
  }

  def list = listAction { page => params =>
    implicit userOptOpt =>
      implicit request =>
        Ok(views.html.userProfile.list(page.copy(list = page.list.map(UserProfile(_))), params))
  }

  def create = createAction { users => groups => implicit userOpt =>
    implicit request =>
      Ok(views.html.userProfile.create(form, VisibilityForm.form, users, groups, routes.UserProfiles.createPost))
  }

  def createPost = createPostAction(form) { formsOrItem =>
    implicit userOpt =>
      implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getGroups { users => groups =>
        BadRequest(views.html.userProfile.create(
          errorForm, accForm, users, groups, routes.UserProfiles.createPost))
      }
      case Right(item) => Redirect(routes.UserProfiles.get(item.id))
          .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt =>
    implicit request =>
      Ok(views.html.userProfile.edit(
        Some(UserProfile(item)), form.fill(UserProfile(item).to), routes.UserProfiles.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { item => formOrItem =>
    implicit userOpt =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) =>
            BadRequest(views.html.userProfile.edit(
              Some(UserProfile(item)), errorForm, routes.UserProfiles.updatePost(id)))
          case Right(item) => Redirect(routes.UserProfiles.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt =>
    implicit request =>
      Ok(views.html.delete(
        UserProfile(item), routes.UserProfiles.deletePost(id),
          routes.UserProfiles.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    // For the users we need to clean up by deleting their profile id, if any...
    userFinder.findByProfileId(id).map(_.delete())
    Redirect(routes.UserProfiles.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def grantList(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = grantListAction(id, page, limit) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionGrantList(UserProfile(item), perms))
  }

  def permissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = setGlobalPermissionsAction(id) {
    item => perms => implicit userOpt => implicit request =>
      Ok(views.html.permissions.editGlobalPermissions(UserProfile(item), perms,
        routes.UserProfiles.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) { item => perms => implicit userOpt =>
    implicit request =>
      Redirect(routes.UserProfiles.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}

