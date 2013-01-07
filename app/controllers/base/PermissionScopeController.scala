package controllers.base

import play.api.mvc.{Call,RequestHeader}
import play.api.libs.concurrent.execution.defaultContext
import models.base._
import models.base.Persistable
import defines._
import models.UserProfile
import acl.GlobalPermissionSet

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait PermissionScopeController[T <: AccessibleEntity] extends PermissionItemController[T] {

  val targetContentTypes: Seq[ContentType.Value]

  val addScopedPermissionAction: String => Call
  val permissionScopeAction: (String, String, String) => Call
  val setPermissionScopeAction: (String, String, String) => Call

  type ManageScopedPermissionViewType = (AccessibleEntity, rest.Page[models.PermissionGrant],
    rest.Page[models.PermissionGrant], Call, Call, UserProfile, RequestHeader) => play.api.templates.Html
  val manageScopedPermissionView: ManageScopedPermissionViewType

  type AddScopedPermissionViewType = (AccessibleEntity,
        Seq[(String,String)], Seq[(String,String)],
        (String,String,String) => Call, UserProfile, RequestHeader) => play.api.templates.Html
  val addScopedPermissionView: AddScopedPermissionViewType

  type PermissionScopeViewType = (AccessibleEntity, Accessor, GlobalPermissionSet[models.base.Accessor],
        Seq[ContentType.Value], Call, UserProfile, RequestHeader) => play.api.templates.Html

  val permissionScopeView: PermissionScopeViewType


  def manageScopedPermissions(id: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>

      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          permGrantsOrErr <- rest.PermissionDAO(user).listForItem(id)
          scopeGrantsOrErr <- rest.PermissionDAO(user).listForScope(id)
        } yield {
          for { item <- itemOrErr.right ; permGrants <- permGrantsOrErr.right ; scopeGrants <- scopeGrantsOrErr.right } yield {
            Ok(manageScopedPermissionView(builder(item), permGrants, scopeGrants, addItemPermissionAction(id), addScopedPermissionAction(id), user, request))
          }
        }
      }
  }

  def addScopedPermissions(id: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>

    implicit val maybeUser = Some(user)
    AsyncRest {
      for {
        itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
        users <- rest.RestHelpers.getUserList
        groups <- rest.RestHelpers.getGroupList
      } yield {
        for { item <- itemOrErr.right } yield {
          Ok(addScopedPermissionView(builder(item), users, groups, permissionScopeAction, user, request))
        }
      }
    }
  }


  def permissionScope(id: String, userType: String, userId: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          userOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
          // FIXME: Faking user for fetching perms to avoid blocking.
          // This means that when we have both the perm set and the user
          // we need to re-assemble them so that the permission set has
          // access to a user's groups to understand inheritance.
          permsOrErr <- rest.PermissionDAO(user)
              .getScope(Accessor(models.Entity.fromString(userId, EntityType.withName(userType))), id)
        } yield {
          for { item <- itemOrErr.right ; accessor <- userOrErr.right; perms <- permsOrErr.right } yield {
            Ok(permissionScopeView(builder(item),  Accessor(accessor), perms.copy(user=Accessor(accessor)), targetContentTypes,
              setPermissionScopeAction(id, userType, userId), user, request))
          }
        }
      }
  }

  def permissionScopePost(id: String, userType: String, userId: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap

      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          accessorOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
        } yield {
          for { item <- itemOrErr.right; accessor <- accessorOrErr.right} yield {
            AsyncRest {
              rest.PermissionDAO(user).setScope(Accessor(accessor), item.id, perms).map { boolOrErr =>
                boolOrErr.right.map { bool =>
                  Redirect(managePermissionAction(id))
                }
              }
            }
          }
        }
      }
  }
}

