package controllers.core

import _root_.controllers.ListParams
import models.base.Accessor
import controllers.base._
import forms.VisibilityForm
import models._
import play.api._
import play.api.i18n.Messages
import defines.{ ContentType, EntityType, PermissionType }
import play.api.libs.concurrent.Execution.Implicits._
import utils.search.Dispatcher
import com.google.inject._
import global.GlobalConfig

class Groups @Inject()(val globalConfig: GlobalConfig) extends PermissionHolderController[Group]
  with VisibilityController[Group]
  with CRUD[GroupF, Group] {

  val entityType = EntityType.Group
  val contentType = ContentType.Group

  val form = models.forms.GroupForm.form

  def get(id: String) = getAction(id) { item => annotations => links =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.group.show(item, annotations))
  }

  def history(id: String) = historyAction(id) {
      item => page => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction {
      page => params => implicit maybeUser => implicit request =>
    Ok(views.html.group.list(page, params))
  }

  def create = createAction {
      users => groups => implicit userOpt => implicit request =>
    Ok(views.html.group.create(form, VisibilityForm.form, users, groups, controllers.core.routes.Groups.createPost))
  }

  def createPost = createPostAction(form) {
      formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.group.create(errorForm, accForm, users, groups, controllers.core.routes.Groups.createPost))
      }
      case Right(item) => Redirect(controllers.core.routes.Groups.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.group.edit(
        item, form.fill(item.model), controllers.core.routes.Groups.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.group.edit(item, errorForm, controllers.core.routes.Groups.updatePost(id)))
      case Right(item) => Redirect(controllers.core.routes.Groups.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, controllers.core.routes.Groups.deletePost(id), controllers.core.routes.Groups.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(controllers.core.routes.Groups.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def grantList(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = grantListAction(id, page, limit) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionGrantList(item, perms))
  }

  def permissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = setGlobalPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.editGlobalPermissions(item, perms,
          controllers.core.routes.Groups.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(controllers.core.routes.Groups.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }


  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
    Ok(views.html.permissions.revokePermission(item, perm,
        controllers.core.routes.Groups.revokePermissionPost(id, permId), controllers.core.routes.Groups.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
    item => bool => implicit userOpt => implicit request =>
      Redirect(controllers.core.routes.Groups.grantList(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  /*
   *	Membership
   */

  /**
   * Present a list of groups to which the current user can be added.
   */
  def membership(userType: String, userId: String) = {
    withItemPermission[Accessor](userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        Async {
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
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def addMember(id: String, userType: String, userId: String) = {
    withItemPermission[Accessor](userId, PermissionType.Grant, ContentType.withName(userType)) {
        item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          groupOrErr <- rest.EntityDAO[Group](entityType, userOpt).get(id)
        } yield {
          groupOrErr.right.map { group =>
            Ok(views.html.group.confirmMembership(group, item,
              controllers.core.routes.Groups.addMemberPost(id, userType, userId)))
          }
        }
      }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def addMemberPost(id: String, userType: String, userId: String) = {
    withItemPermission[Accessor](userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        AsyncRest {
          rest.PermissionDAO(userOpt).addGroup(id, userId).map { boolOrErr =>
            boolOrErr.right.map { ok =>
              Redirect(controllers.core.routes.Groups.membership(userType, userId))
                .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
            }
          }
        }
    }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def removeMember(id: String, userType: String, userId: String) = {
    withItemPermission[Accessor](userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        AsyncRest {
          for {
            groupOrErr <- rest.EntityDAO[Group](entityType, userOpt).get(id)
          } yield {
            groupOrErr.right.map { group =>
              Ok(views.html.group.removeMembership(group, item,
                controllers.core.routes.Groups.removeMemberPost(id, userType, userId)))
            }
          }
        }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def removeMemberPost(id: String, userType: String, userId: String) = {
    withItemPermission[Accessor](userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        AsyncRest {
          rest.PermissionDAO(userOpt).removeGroup(id, userId).map { boolOrErr =>
            boolOrErr.right.map { ok =>
              Redirect(controllers.core.routes.Groups.membership(userType, userId))
                .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
            }
          }
        }
    }
  }
}
