package controllers

import models.{UserProfile}
import models.base.AccessibleEntity
import defines._
import play.api.libs.concurrent.execution.defaultContext
import rest.EntityDAO
import controllers.base.CRUD
import controllers.base.VisibilityController
import controllers.base.PermissionsController
import models.UserProfileRepr


object UserProfiles extends PermissionsController[UserProfile,UserProfileRepr]
		with VisibilityController[UserProfile,UserProfileRepr]
		with CRUD[UserProfile,UserProfileRepr] {
  val entityType = EntityType.UserProfile
  val contentType = ContentType.UserProfile
  val listAction = routes.UserProfiles.list _
  val createAction = routes.UserProfiles.createPost
  val updateAction = routes.UserProfiles.updatePost _
  val cancelAction = routes.UserProfiles.get _
  val deleteAction = routes.UserProfiles.deletePost _
  
  val permsAction = routes.UserProfiles.permissions _
  val setPermsAction = routes.UserProfiles.permissionsPost _
  
  val setVisibilityAction = routes.UserProfiles.visibilityPost _
  val visibilityAction = routes.UserProfiles.visibility _
  val visibilityView = views.html.visibility.apply _
  
  val form = forms.UserProfileForm.form
  val showAction = routes.UserProfiles.get _
  val formView = views.html.userProfile.edit.apply _
  val showView = views.html.userProfile.show.apply _
  val listView = views.html.userProfile.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.accessors.edit.apply _
  val builder = UserProfileRepr
}

