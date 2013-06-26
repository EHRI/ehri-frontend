package controllers

import _root_.models.base.AccessibleEntity
import models.forms.{VisibilityForm}
import _root_.models._
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import _root_.controllers.base._
import collection.immutable.ListMap
import solr.SearchParams
import scala.Some


object UserProfiles extends PermissionHolderController[UserProfileMeta]
  with EntityRead[UserProfileMeta]
  with EntityUpdate[UserProfileF,UserProfileMeta]
  with EntityDelete[UserProfileMeta]
  with EntitySearch {

  val DEFAULT_SORT = "name"

  val listFilterMappings: ListMap[String,String] = ListMap(
    "name" -> "name",
    Entity.IDENTIFIER -> Entity.IDENTIFIER
  )

  val orderMappings: ListMap[String,String] = ListMap(
    "name" -> "name",
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

  def get(id: String) = getAction(id) {
      item => annotations => links => implicit userOptOpt => implicit request =>
    Ok(views.html.userProfile.show(item, annotations))
  }

  def search = {
    searchAction(defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
        Ok(views.html.userProfile.search(page, params, facets, routes.UserProfiles.search))
    }
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction { page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.userProfile.list(page, params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.userProfile.edit(
        item, form.fill(item.model), routes.UserProfiles.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.userProfile.edit(
          item, errorForm, routes.UserProfiles.updatePost(id)))
      case Right(item) => Redirect(routes.UserProfiles.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(item, routes.UserProfiles.deletePost(id),
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
    Ok(views.html.permissions.permissionGrantList(item, perms))
  }

  def permissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = setGlobalPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.editGlobalPermissions(item, perms,
        routes.UserProfiles.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(routes.UserProfiles.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
        Ok(views.html.permissions.revokePermission(item, perm,
          routes.UserProfiles.revokePermissionPost(id, permId), routes.UserProfiles.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
    item => bool => implicit userOpt => implicit request =>
      Redirect(routes.UserProfiles.grantList(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}

