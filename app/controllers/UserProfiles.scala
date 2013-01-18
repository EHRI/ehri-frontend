package controllers

import play.api._
import play.api.mvc._
import defines._
import base.{PermissionItemController, CRUD, VisibilityController, PermissionHolderController}
import models.{Group, UserProfile}
import models.forms.UserProfileF


object UserProfiles extends PermissionHolderController[UserProfileF,UserProfile]
	with VisibilityController[UserProfileF,UserProfile]
	with CRUD[UserProfileF,UserProfile]
  with PermissionItemController[UserProfile] {

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


  val managePermissionAction = routes.UserProfiles.managePermissions _
  val managePermissionView = views.html.permissions.managePermissions.apply _
  val addItemPermissionAction = routes.UserProfiles.addItemPermissions _
  val addItemPermissionView = views.html.permissions.permissionItem.apply _
  val permissionItemAction = routes.UserProfiles.permissionItem _
  val permissionItemView = views.html.permissions.setPermissionItem.apply _
  val setPermissionItemAction = routes.UserProfiles.permissionItemPost _

  val entityType = EntityType.UserProfile
  val contentType = ContentType.UserProfile
  val createAction = routes.UserProfiles.createPost
  val updateAction = routes.UserProfiles.updatePost _
  val cancelAction = routes.UserProfiles.get _
  val deleteAction = routes.UserProfiles.deletePost _
  
  val permsAction = routes.UserProfiles.permissions _
  val setPermsAction = routes.UserProfiles.permissionsPost _
  
  val setVisibilityAction = routes.UserProfiles.visibilityPost _
  val visibilityAction = routes.UserProfiles.visibility _
  val visibilityView = views.html.visibility.apply _
  
  val form = models.forms.UserProfileForm.form
  val showAction = routes.UserProfiles.get _
  val formView = views.html.userProfile.edit.apply _
  val showView = views.html.userProfile.show.apply _
  val listView = views.html.userProfile.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.accessors.edit.apply _
  val permListView = views.html.accessors.permissionGrantList.apply _
  // NB: Because the UserProfile class has more optional
  // parameters we use the companion object apply method here.
  val builder = UserProfile.apply _
}

