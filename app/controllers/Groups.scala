package controllers

import _root_.models.base.Accessor
import play.api._
import base.CRUD
import base.PermissionHolderController
import base.VisibilityController
import defines.{ ContentType, EntityType, PermissionType }
import play.api.libs.concurrent.execution.defaultContext
import models.forms.GroupF
import models.Group
import models.base.Accessor

object Groups extends PermissionHolderController[GroupF, Group]
  with VisibilityController[GroupF, Group]
  with CRUD[GroupF, Group] {
  val entityType = EntityType.Group
  val contentType = ContentType.Group
  val listAction = routes.Groups.list _
  val createAction = routes.Groups.createPost
  val updateAction = routes.Groups.updatePost _
  val cancelAction = routes.Groups.get _
  val deleteAction = routes.Groups.deletePost _
  val permsAction = routes.Groups.permissions _
  val setPermsAction = routes.Groups.permissionsPost _

  val setVisibilityAction = routes.Groups.visibilityPost _
  val visibilityAction = routes.Groups.visibility _
  val visibilityView = views.html.visibility.apply _

  val form = models.forms.GroupForm.form
  val showAction = routes.Groups.get _
  val formView = views.html.group.edit.apply _
  val showView = views.html.group.show.apply _
  val listView = views.html.group.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.accessors.edit.apply _
  val permListView = views.html.accessors.permissionGrantList.apply _
  val builder = Group

  /*
   *	Membership
   */

  /**
   * Present a list of groups to which the current user can be added.
   */
  def membership(userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
          groups <- rest.RestHelpers.getGroupList
        } yield {
          itemOrErr.right.map { item =>
            // filter out the groups the user already belongs to
            val accessor = Accessor(item)
            val filteredGroups = groups.filter(t => t._1 != accessor.id).filter {
              case (ident, name) =>
                // if the user is admin, they can add the user to ANY group
                if (user.isAdmin) {
                  !accessor.groups.map(_.id).contains(ident)
                } else {
                  // if not, they can add the user to groups they belong to
                  // TODO: Enforce these policies with the permission system!
                  // TODO: WRITE TESTS FOR THESE WEIRD BEHAVIOURS!!!
                  (!accessor.groups.map(_.id).contains(ident)) &&
                    user.groups.map(_.id).contains(ident)
                }
            }
            Ok(views.html.group.membership(accessor, filteredGroups, user, request))
          }
        }
      }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def addMember(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          groupOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          itemOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
        } yield {
          itemOrErr.right.map { item =>
            val group = builder(groupOrErr.right.get)
            Ok(views.html.group.confirmMembership(group, Accessor(itemOrErr.right.get),
              routes.Groups.addMemberPost(id, userType, userId), user, request))
          }
        }
      }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def addMemberPost(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.PermissionDAO(user).addGroup(id, userId).map { boolOrErr =>
          boolOrErr.right.map { ok =>
            Redirect(routes.Groups.membership(userType, userId))
          }
        }
      }
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def removeMember(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          groupOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          itemOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
        } yield {
          itemOrErr.right.map { item =>
            val group = builder(groupOrErr.right.get)
            Ok(views.html.group.removeMembership(group, Accessor(itemOrErr.right.get),
              routes.Groups.removeMemberPost(id, userType, userId), user, request))
          }
        }
      }
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def removeMemberPost(id: String, userType: String, userId: String) = withItemPermission(userId, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.PermissionDAO(user).removeGroup(id, userId).map { boolOrErr =>
          boolOrErr.right.map { ok =>
            Redirect(routes.Groups.membership(userType, userId))
          }
        }
      }
  }
}
