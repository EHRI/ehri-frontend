package controllers

import _root_.models.base.AccessibleEntity
import models.forms.{VisibilityForm}
import _root_.models.{PermissionGrant, Entity, UserProfile, UserProfileF}
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import base._
import collection.immutable.ListMap
import solr.{SearchOrder, SearchParams}


object UserProfiles extends PermissionHolderController[UserProfile]
  with EntityRead[UserProfile]
  with EntityUpdate[UserProfileF,UserProfile]
  with EntityDelete[UserProfile]
  with EntitySearch {

  val DEFAULT_SORT = AccessibleEntity.NAME

  val listFilterMappings: ListMap[String,String] = ListMap(
    AccessibleEntity.NAME -> AccessibleEntity.NAME,
    Entity.IDENTIFIER -> Entity.IDENTIFIER
  )

  val orderMappings: ListMap[String,String] = ListMap(
    AccessibleEntity.NAME -> AccessibleEntity.NAME,
    Entity.IDENTIFIER -> Entity.IDENTIFIER
  )


  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(listFilterMappings, orderMappings, Some(DEFAULT_SORT))
  }



  val entityType = EntityType.UserProfile
  val contentType = ContentType.UserProfile

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))

  val form = models.forms.UserProfileForm.form

  // NB: Because the UserProfile class has more optional
  // parameters we use the companion object apply method here.
  val builder = UserProfile.apply _

  def get(id: String) = getAction(id) {
      item => annotations => links => implicit userOptOpt => implicit request =>
    Ok(views.html.userProfile.show(UserProfile(item), annotations))
  }

  def search = {
    searchAction(defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
        Ok(views.html.userProfile.search(page, params, facets, routes.UserProfiles.search))
    }
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(UserProfile(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.userProfile.list(page.copy(items = page.items.map(UserProfile(_))), params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.userProfile.edit(
        UserProfile(item), form.fill(UserProfile(item).formable), routes.UserProfiles.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.userProfile.edit(
          UserProfile(item), errorForm, routes.UserProfiles.updatePost(id)))
      case Right(item) => Redirect(routes.UserProfiles.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        UserProfile(item), routes.UserProfiles.deletePost(id),
          routes.UserProfiles.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    // For the users we need to clean up by deleting their profile id, if any...
    userFinder.findByProfileId(id).map(_.delete())
    Redirect(routes.UserProfiles.search())
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

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(routes.UserProfiles.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
        Ok(views.html.permissions.revokePermission(UserProfile(item), PermissionGrant(perm),
          routes.UserProfiles.revokePermissionPost(id, permId), routes.UserProfiles.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
    item => bool => implicit userOpt => implicit request =>
      Redirect(routes.UserProfiles.grantList(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}

