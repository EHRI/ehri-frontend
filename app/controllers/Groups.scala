package controllers

import controllers.base.CRUD
import controllers.base.PermissionsController
import controllers.base.VisibilityController
import defines.{ContentType,EntityType}
import models.Group
import models.GroupRepr
import play.api.libs.concurrent.execution.defaultContext
import models.UserProfileRepr
import models.base.Accessor

object Groups extends PermissionsController[Group, GroupRepr] 
		with VisibilityController[Group,GroupRepr]
		with CRUD[Group, GroupRepr] {
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
  
  val form = forms.GroupForm.form
  val showAction = routes.Groups.get _
  val formView = views.html.group.edit.apply _
  val showView = views.html.group.show.apply _
  val listView = views.html.accessors.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.accessors.edit.apply _
  val builder = GroupRepr
  
  /*
   *	Membership
   */
  
    /**
   * Present a list of groups to which the current user can be added.
   */
  def membership(userType: String, userId: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(EntityType.withName(userType), Some(userProfile)).get(userId)
            groups <- rest.RestHelpers.getGroupList
          } yield {
            itemOrErr.right.map { item =>
              // filter out the groups the user already belongs to
              val user = Accessor(item)
              val filteredGroups = groups.filter(t => t._1 != user.identifier).filter {
                case (ident, name) =>
                  // if the user is admin, they can add the user to ANY group
                  if (userProfile.isAdmin) {
                    !user.groups.map(_.identifier).contains(ident)
                  } else {
                    // if not, they can add the user to groups they belong to
                    // TODO: Enforce these policies with the permission system!
                    (!user.groups.map(_.identifier).contains(ident)) &&
                      userProfile.groups.map(_.identifier).contains(ident)
                  }
              }
              Ok(views.html.group.membership(user, filteredGroups, maybeUser, request))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }

  /**
   * Confirm adding the given user to the specified group.
   */
  def addMember(id: String, userType: String, userId: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          for {
            groupOrErr <- rest.EntityDAO(entityType, Some(userProfile)).get(id)
            itemOrErr <- rest.EntityDAO(EntityType.withName(userType), Some(userProfile)).get(userId)            
          } yield {
            itemOrErr.right.map { item =>
              val group = builder(groupOrErr.right.get)
              Ok(views.html.group.confirmMembership(group, Accessor(itemOrErr.right.get),
            		  routes.Groups.addMemberPost(id, userType, userId), maybeUser, request))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def addMemberPost(id: String, userType: String, userId: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          rest.PermissionDAO(userProfile).addGroup(id, userId).map { boolOrErr =>
            boolOrErr.right.map { ok =>
              Redirect(routes.Groups.membership(userType, userId))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }
  
  /**
   * Confirm adding the given user to the specified group.
   */
  def removeMember(id: String, userType: String, userId: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          for {
            groupOrErr <- rest.EntityDAO(entityType, Some(userProfile)).get(id)
            itemOrErr <- rest.EntityDAO(EntityType.withName(userType), Some(userProfile)).get(userId)            
          } yield {
            itemOrErr.right.map { item =>
              val group = builder(groupOrErr.right.get)
              Ok(views.html.group.removeMembership(group, Accessor(itemOrErr.right.get),
            		  routes.Groups.removeMemberPost(id, userType, userId), maybeUser, request))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }

  /**
   * Add the user to the group and redirect to the show view.
   */
  def removeMemberPost(id: String, userType: String, userId: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          rest.PermissionDAO(userProfile).removeGroup(id, userId).map { boolOrErr =>
            boolOrErr.right.map { ok =>
              Redirect(routes.Groups.membership(userType, userId))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }  
}
