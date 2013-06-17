package controllers

import models.base.Accessor
import _root_.models.{PermissionGrant, UserProfile, Group, GroupF}
import models.forms.VisibilityForm
import play.api._
import play.api.i18n.Messages
import base._
import defines.{ ContentType, EntityType, PermissionType }
import play.api.libs.concurrent.Execution.Implicits._

object Groups extends PermissionHolderController[Group]
  with VisibilityController[Group]
  with CRUD[GroupF, Group] {

  val entityType = EntityType.Group
  val contentType = ContentType.Group

  val form = models.forms.GroupForm.form
  val builder = Group

  def get(id: String) = getAction(id) { item => annotations => links =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.group.show(Group(item), annotations))
  }

  def history(id: String) = historyAction(id) {
      item => page => implicit userOptOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(Group(item), page, ListParams()))
  }

  def list = listAction {
      page => params => implicit maybeUser => implicit request =>
    Ok(views.html.group.list(page.copy(items = page.items.map(Group(_))), params))
  }

  def create = createAction {
      users => groups => implicit userOpt => implicit request =>
    Ok(views.html.group.create(form, VisibilityForm.form, users, groups, routes.Groups.createPost))
  }

  def createPost = createPostAction(form) {
      formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.group.create(errorForm, accForm, users, groups, routes.Groups.createPost))
      }
      case Right(item) => Redirect(routes.Groups.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.group.edit(
        Group(item), form.fill(Group(item).formable), routes.Groups.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.group.edit(Group(item), errorForm, routes.Groups.updatePost(id)))
      case Right(item) => Redirect(routes.Groups.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        Group(item), routes.Groups.deletePost(id), routes.Groups.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(routes.Groups.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def grantList(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = grantListAction(id, page, limit) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionGrantList(Group(item), perms))
  }

  def permissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = setGlobalPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.editGlobalPermissions(UserProfile(item), perms,
        routes.Groups.permissionsPost(id)))
  }

  def permissionsPost(id: String) = setGlobalPermissionsPostAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Redirect(routes.Groups.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }


  def revokePermission(id: String, permId: String) = revokePermissionAction(id, permId) {
      item => perm => implicit userOpt => implicit request =>
    Ok(views.html.permissions.revokePermission(Group(item), PermissionGrant(perm),
        routes.Groups.revokePermissionPost(id, permId), routes.Groups.grantList(id)))
  }

  def revokePermissionPost(id: String, permId: String) = revokePermissionActionPost(id, permId) {
    item => bool => implicit userOpt => implicit request =>
      Redirect(routes.Groups.grantList(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  /*
   *	Membership
   */

  /**
   * Present a list of groups to which the current user can be added.
   */
  def membership(userType: String, userId: String) = {
    withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        Async {
          for {
            groups <- rest.RestHelpers.getGroupList
          } yield {
            // filter out the groups the user already belongs to
            val accessor = Accessor(item)
            val filteredGroups = groups.filter(t => t._1 != accessor.id).filter {
              case (ident, name) =>
                // if the user is admin, they can add the user to ANY group
                if (userOpt.get.isAdmin) {
                  !accessor.groups.map(_.id).contains(ident)
                } else {
                  // if not, they can add the user to groups they belong to
                  // TODO: Enforce these policies with the permission system!
                  // TODO: WRITE TESTS FOR THESE WEIRD BEHAVIOURS!!!
                  (!accessor.groups.map(_.id).contains(ident)) &&
                    userOpt.get.groups.map(_.id).contains(ident)
                }
            }
            Ok(views.html.group.membership(accessor, filteredGroups))
          }
        }
    }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def addMember(id: String, userType: String, userId: String) = {
    withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        AsyncRest {
          for {
            groupOrErr <- rest.EntityDAO(entityType, userOpt).get(id)
          } yield {
            groupOrErr.right.map { group =>
              Ok(views.html.group.confirmMembership(builder(group), Accessor(item),
                routes.Groups.addMemberPost(id, userType, userId)))
            }
          }
        }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def addMemberPost(id: String, userType: String, userId: String) = {
    withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        AsyncRest {
          rest.PermissionDAO(userOpt).addGroup(id, userId).map { boolOrErr =>
            boolOrErr.right.map { ok =>
              Redirect(routes.Groups.membership(userType, userId))
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
    withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        AsyncRest {
          for {
            groupOrErr <- rest.EntityDAO(entityType, userOpt).get(id)
          } yield {
            groupOrErr.right.map { group =>
              Ok(views.html.group.removeMembership(builder(group), Accessor(item),
                routes.Groups.removeMemberPost(id, userType, userId)))
            }
          }
        }
    }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def removeMemberPost(id: String, userType: String, userId: String) = {
    withItemPermission(userId, PermissionType.Grant, ContentType.withName(userType)) {
      item => implicit userOpt => implicit request =>
        AsyncRest {
          rest.PermissionDAO(userOpt).removeGroup(id, userId).map { boolOrErr =>
            boolOrErr.right.map { ok =>
              Redirect(routes.Groups.membership(userType, userId))
                .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
            }
          }
        }
    }
  }
}
