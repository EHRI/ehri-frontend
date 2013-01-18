package controllers

import play.api._
import play.api.mvc._
import defines._
import base.{PermissionItemController, CRUD, VisibilityController, PermissionHolderController}
import models.{Group, UserProfile}
import models.forms.UserProfileF
import org.bouncycastle.asn1.cms.OtherKeyAttribute


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
        Ok(views.html.userProfile.show(UserProfile(item), maybeUser, request))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.userProfile.list(page.copy(list = page.list.map(UserProfile(_))), maybeUser, request))
  }

  def create = withContentPermission(PermissionType.Create, contentType) { implicit user =>
    implicit request =>
      Ok(views.html.userProfile.edit(None, form, routes.UserProfiles.createPost, user, request))
  }

  def createPost = createPostAction(form) { formOrItem =>
    implicit user =>
      implicit request =>
    formOrItem match {
      case Left(form) => BadRequest(views.html.userProfile.edit(None, form, routes.UserProfiles.createPost, user, request))
      case Right(item) => Redirect(routes.UserProfiles.get(item.id))
          .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.userProfile.edit(
        Some(UserProfile(item)), form.fill(UserProfile(item).to), routes.UserProfiles.updatePost(id), user, request))
  }

  def updatePost(id: String) = updatePostAction(id, form) { formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) => getEntity(id, Some(user)) { item =>
            BadRequest(views.html.userProfile.edit(
              Some(UserProfile(item)), errorForm, routes.UserProfiles.updatePost(id), user, request))
          }
          case Right(item) => Redirect(routes.UserProfiles.get(item.id))
            .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        UserProfile(item), routes.UserProfiles.deletePost(id),
          routes.UserProfiles.get(id), user, request))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.UserProfiles.list())
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.visibility(UserProfile(item), users, groups, routes.UserProfiles.visibilityPost(id), user, request))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.UserProfiles.list())
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", id))
  }

  def grantList(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = grantListAction(id, page, limit) {
      item => perms => implicit user => implicit request =>
    Ok(views.html.accessors.permissionGrantList(UserProfile(item), perms, user, request))
  }

  def permissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = setGlobalPermissionsAction(id) {
    item => perms => implicit user => implicit request =>
      Ok(views.html.accessors.edit(UserProfile(item), perms,
        routes.UserProfiles.permissionsPost(id), user, request))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) { item => perms => implicit user =>
    implicit request =>
      Redirect(routes.UserProfiles.get(id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = manageItemPermissionsAction(id, page, limit) {
    item => perms => implicit user => implicit request =>
      Ok(views.html.permissions.managePermissions(Group(item), perms,
        routes.UserProfiles.addItemPermissions(id), user, request))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(UserProfile(item), users, groups,
        routes.UserProfiles.setItemPermissions _, user, request))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(UserProfile(item), accessor, perms, contentType,
        routes.UserProfiles.setItemPermissionsPost(id, userType, userId), user, request))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    perms => implicit user => implicit request =>
      Redirect(routes.UserProfiles.managePermissions(id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", id))
  }
}

