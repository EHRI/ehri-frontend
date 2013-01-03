package controllers

import play.api._
import play.api.mvc._
import defines._
import base.CRUD
import base.VisibilityController
import base.PermissionHolderController
import models.UserProfile
import models.forms.UserProfileF


object UserProfiles extends PermissionHolderController[UserProfileF,UserProfile]
		with VisibilityController[UserProfileF,UserProfile]
		with CRUD[UserProfileF,UserProfile] {
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
  
  val form = models.forms.UserProfileForm.form
  val showAction = routes.UserProfiles.get _
  val formView = views.html.userProfile.edit.apply _
  val showView = views.html.userProfile.show.apply _
  val listView = views.html.userProfile.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.accessors.edit.apply _
  // NB: Because the UserProfile class has more optional
  // parameters we use the companion object apply method here.
  val builder = UserProfile.apply _
}

