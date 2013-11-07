package controllers.core

import _root_.models.json.RestResource
import play.api.libs.concurrent.Execution.Implicits._
import controllers.base._
import forms.VisibilityForm
import models._
import models.base.Accessor
import play.api._
import play.api.i18n.Messages
import defines.{ ContentTypes, EntityType, PermissionType }
import global.GlobalConfig
import com.google.inject._
import utils.search.Dispatcher
import scala.concurrent.Future

class Groups @Inject()(implicit val globalConfig: GlobalConfig, val searchDispatcher: Dispatcher) extends PermissionHolderController[Group]
  with VisibilityController[Group]
  with CRUD[GroupF, Group] {

  val entityType = EntityType.Group
  val contentType = ContentTypes.Group

  implicit val resource = Group.Resource

  private val form = models.forms.GroupForm.form
  private val groupRoutes = controllers.core.routes.Groups

  def get(id: String) = getWithChildrenAction[Accessor](id) {
      item => page => params => annotations => links => implicit maybeUser => implicit request =>
    Ok(views.html.group.show(item, page, params, annotations))
  }

  def history(id: String) = historyAction(id) {
      item => page => params => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction {
      page => params => implicit maybeUser => implicit request =>
    Ok(views.html.group.list(page, params))
  }

  def create = createAction {
      users => groups => implicit userOpt => implicit request =>
    Ok(views.html.group.create(form, VisibilityForm.form,
        users, groups, groupRoutes.createPost))
  }

  def createPost = createPostAction.async(form) {
      formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.group.create(
          errorForm, accForm, users, groups, groupRoutes.createPost))
      }
      case Right(item) => Future.successful(Redirect(groupRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id)))
    }
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.group.edit(
        item, form.fill(item.model), groupRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.group.edit(item, errorForm, groupRoutes.updatePost(id)))
      case Right(item) => Redirect(groupRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, groupRoutes.deletePost(id), groupRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(groupRoutes.list)
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def grantList(id: String) = grantListAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionGrantList(item, perms))
  }

  def permissions(id: String) = setGlobalPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.editGlobalPermissions(item, perms,
          groupRoutes.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(groupRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }


  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
    Ok(views.html.permissions.revokePermission(item, perm,
        groupRoutes.revokePermissionPost(id, permId), groupRoutes.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
      item => bool => implicit userOpt => implicit request =>
    Redirect(groupRoutes.grantList(id))
      .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  /*
   *	Membership
   */

  /**
   * Present a list of groups to which the current user can be added.
   */
  def membership(userType: String, userId: String) = {
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      for {
        groups <- rest.RestHelpers.getGroupList
      } yield {
        // filter out the groups the user already belongs to
        val filteredGroups = groups.filter(t => t._1 != item.id).filter {
          case (ident, name) =>
            // if the user is admin, they can add the user to ANY group
            if (userOpt.get.isAdmin) {
              !item.groups.map(_.id).contains(ident)
            } else {
              // if not, they can add the user to groups they belong to
              // TODO: Enforce these policies with the permission system!
              // TODO: WRITE TESTS FOR THESE WEIRD BEHAVIOURS!!!
              (!item.groups.map(_.id).contains(ident)) &&
                userOpt.get.groups.map(_.id).contains(ident)
            }
        }
        Ok(views.html.group.membership(item, filteredGroups))
      }
    }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def addMember(id: String, userType: String, userId: String) = {
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      rest.EntityDAO().get[Group](id).map { group =>
        Ok(views.html.group.confirmMembership(group, item,
          groupRoutes.addMemberPost(id, userType, userId)))
      }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def addMemberPost(id: String, userType: String, userId: String) = {
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      rest.PermissionDAO().addGroup(id, userId).map { ok =>
        Redirect(groupRoutes.membership(userType, userId))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def removeMember(id: String, userType: String, userId: String) = {
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      rest.EntityDAO().get[Group](id).map { group =>
        Ok(views.html.group.removeMembership(group, item,
          groupRoutes.removeMemberPost(id, userType, userId)))
      }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def removeMemberPost(id: String, userType: String, userId: String) = {
    withItemPermission.async[Accessor](userId, PermissionType.Grant, ContentTypes.withName(userType)) {
        item => implicit userOpt => implicit request =>
      rest.PermissionDAO().removeGroup(id, userId).map { ok =>
        Redirect(groupRoutes.membership(userType, userId))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}
