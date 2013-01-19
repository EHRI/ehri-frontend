package controllers

import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import base.{PermissionItemController, CRUD, VisibilityController, PermissionHolderController}
import models.{Group, UserProfile}
import models.forms.UserProfileF


object UserProfiles extends PermissionHolderController[UserProfileF,UserProfile]
	with VisibilityController[UserProfileF,UserProfile]
	with CRUD[UserProfileF,UserProfile]
  with PermissionItemController[UserProfile] {



  val entityType = EntityType.UserProfile
  val contentType = ContentType.UserProfile

  val form = models.forms.UserProfileForm.form

  // NB: Because the UserProfile class has more optional
  // parameters we use the companion object apply method here.
  val builder = UserProfile.apply _

  def get(id: String) = getAction(id) { item =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.userProfile.show(UserProfile(item)))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.userProfile.list(page.copy(list = page.list.map(UserProfile(_)))))
  }

  def create = withContentPermission(PermissionType.Create, contentType) { implicit user =>
    implicit request =>
      Ok(views.html.userProfile.edit(None, form, routes.UserProfiles.createPost))
  }

  def createPost = createPostAction(form) { formOrItem =>
    implicit user =>
      implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.userProfile.edit(
        None, errorForm, routes.UserProfiles.createPost))
      case Right(item) => Redirect(routes.UserProfiles.get(item.id))
          .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.userProfile.edit(
        Some(UserProfile(item)), form.fill(UserProfile(item).to), routes.UserProfiles.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) => getEntity(id, Some(user)) { item =>
            BadRequest(views.html.userProfile.edit(
              Some(UserProfile(item)), errorForm, routes.UserProfiles.updatePost(id)))
          }
          case Right(item) => Redirect(routes.UserProfiles.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        UserProfile(item), routes.UserProfiles.deletePost(id),
          routes.UserProfiles.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.UserProfiles.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.visibility(UserProfile(item), users, groups, routes.UserProfiles.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.UserProfiles.list())
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def grantList(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = grantListAction(id, page, limit) {
      item => perms => implicit user => implicit request =>
    Ok(views.html.accessors.permissionGrantList(UserProfile(item), perms))
  }

  def permissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = setGlobalPermissionsAction(id) {
    item => perms => implicit user => implicit request =>
      Ok(views.html.accessors.edit(UserProfile(item), perms,
        routes.UserProfiles.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) { item => perms => implicit user =>
    implicit request =>
      Redirect(routes.UserProfiles.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = manageItemPermissionsAction(id, page, limit) {
    item => perms => implicit user => implicit request =>
      Ok(views.html.permissions.managePermissions(Group(item), perms,
        routes.UserProfiles.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(UserProfile(item), users, groups,
        routes.UserProfiles.setItemPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(UserProfile(item), accessor, perms, contentType,
        routes.UserProfiles.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    perms => implicit user => implicit request =>
      Redirect(routes.UserProfiles.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}

