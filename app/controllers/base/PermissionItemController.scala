package controllers.base

import play.api.mvc.{Call,RequestHeader}
import play.api.libs.concurrent.execution.defaultContext
import models.base._
import defines._
import models.UserProfile
import acl.{ItemPermissionSet, GlobalPermissionSet}

/**
 * Trait for setting permissions on an individual item.
 *
 * @tparam T the entity's build class
 */
trait PermissionItemController[T <: AccessibleEntity] extends EntityRead[T] {

  val addItemPermissionAction: String => Call
  val permissionItemAction: (String, String, String) => Call
  val setPermissionItemAction: (String, String, String) => Call


  type AddItemPermissionViewType = (AccessibleEntity,
        Seq[(String,String)], Seq[(String,String)],
        (String,String,String) => Call, UserProfile, RequestHeader) => play.api.templates.Html
  val addItemPermissionView: AddItemPermissionViewType

  type PermissionItemViewType = (AccessibleEntity, Accessor, ItemPermissionSet[models.base.Accessor],
        ContentType.Value, Call, UserProfile, RequestHeader) => play.api.templates.Html

  val permissionItemView: PermissionItemViewType


  def addItemPermissions(id: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>

    implicit val maybeUser = Some(user)
    AsyncRest {
      for {
        itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
        users <- rest.RestHelpers.getUserList
        groups <- rest.RestHelpers.getGroupList
      } yield {
        for { item <- itemOrErr.right } yield {
          Ok(addItemPermissionView(builder(item), users, groups, permissionItemAction, user, request))
        }
      }
    }
  }


  def permissionItem(id: String, userType: String, userId: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
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
              .getItem(Accessor(models.Entity.fromString(userId, EntityType.withName(userType))), contentType, id)
        } yield {
          for { item <- itemOrErr.right ; accessor <- userOrErr.right; perms <- permsOrErr.right } yield {
            Ok(permissionItemView(builder(item),  Accessor(accessor), perms.copy(user=Accessor(accessor)), contentType,
              setPermissionItemAction(id, userType, userId), user, request))
          }
        }
      }
  }

  def permissionItemPost(id: String, userType: String, userId: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: List[String] = data.get(contentType.toString).map(_.toList).getOrElse(List())

      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          accessorOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
        } yield {
          for { item <- itemOrErr.right; accessor <- accessorOrErr.right} yield {
            AsyncRest {
              rest.PermissionDAO(user).setItem(Accessor(accessor), contentType, item.id, perms).map { boolOrErr =>
                boolOrErr.right.map { bool =>
                  Redirect(showAction(id))
                }
              }
            }
          }
        }
      }
  }
}

