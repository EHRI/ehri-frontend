package controllers.core

import controllers.generic._
import models.{AccountDAO, IsadG, UserProfile, UserProfileF}
import play.api.i18n.Messages
import defines.ContentTypes
import utils.search.{FacetSort, Resolver, SearchParams, Dispatcher}
import com.google.inject._
import backend.Backend
import solr.facet.{FieldFacetClass, SolrQueryFacet, QueryFacetClass}
import views.Helpers

@Singleton
case class UserProfiles @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends PermissionHolder[UserProfile]
  with Read[UserProfile]
  with Update[UserProfileF,UserProfile]
  with Delete[UserProfile]
  with Search {

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key="groupName",
        name=Messages("contentTypes.group"),
        param="group",
        sort = FacetSort.Name
      )
    )
  }

  implicit val resource = UserProfile.Resource

  val contentType = ContentTypes.UserProfile

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(resource.entityType))

  val form = models.UserProfile.form

  private val userRoutes = controllers.core.routes.UserProfiles
  

  def get(id: String) = getAction(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.userProfile.show(item, annotations))
  }

  def search = {
    searchAction[UserProfile](defaultParams = Some(DEFAULT_SEARCH_PARAMS), entityFacets = entityFacets) {
        page => params => facets => implicit userOpt => implicit request =>
      Ok(views.html.userProfile.search(page, params, facets, userRoutes.search))
    }
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.userProfile.list(page, params))
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.userProfile.edit(
        item, form.fill(item.model), userRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.userProfile.edit(
          item, errorForm, userRoutes.updatePost(id)))
      case Right(item) => Redirect(userRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(item, userRoutes.deletePost(id),
          userRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    // For the users we need to clean up by deleting their profile id, if any...
    userDAO.findByProfileId(id).map(_.delete())
    Redirect(userRoutes.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def grantList(id: String) = grantListAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionGrantList(item, perms))
  }

  def permissions(id: String) = setGlobalPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.editGlobalPermissions(item, perms,
        userRoutes.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(userRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
        Ok(views.html.permissions.revokePermission(item, perm,
          userRoutes.revokePermissionPost(id, permId), userRoutes.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
    item => bool => implicit userOpt => implicit request =>
      Redirect(userRoutes.grantList(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}

