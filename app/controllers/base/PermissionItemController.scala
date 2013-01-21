package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models.{PermissionGrant, Entity, UserProfile}
import acl.{ItemPermissionSet, GlobalPermissionSet}

/**
 * Trait for setting permissions on an individual item.
 *
 * @tparam T the entity's build class
 */
trait PermissionItemController[T <: AccessibleEntity] extends EntityRead[T] {

  def manageItemPermissionsAction(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT)(
      f: Entity => rest.Page[PermissionGrant] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit user =>
      implicit request =>

        implicit val maybeUser = Some(user)
        AsyncRest {
          for {
            permGrantsOrErr <- rest.PermissionDAO(user).listForItem(id, math.max(page, 1), math.max(limit, 1))
          } yield {
            for { permGrants <- permGrantsOrErr.right } yield {
              f(item)(permGrants)(user)(request)
            }
          }
        }
    }
  }

  def addItemPermissionsAction(id: String)(f: Entity => Seq[(String,String)] => Seq[(String,String)] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit user =>
      implicit request =>

        implicit val maybeUser = Some(user)
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
            users <- rest.RestHelpers.getUserList
            groups <- rest.RestHelpers.getGroupList
          } yield {
            for { item <- itemOrErr.right } yield {
              f(item)(users)(groups)(user)(request)
            }
          }
        }
    }
  }


  def setItemPermissionsAction(id: String, userType: String, userId: String)(
      f: Entity => Accessor => acl.ItemPermissionSet[Accessor] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit user =>
        implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          userOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
          // FIXME: Faking user for fetching perms to avoid blocking.
          // This means that when we have both the perm set and the user
          // we need to re-assemble them so that the permission set has
          // access to a user's groups to understand inheritance.
          permsOrErr <- rest.PermissionDAO(user)
              .getItem(Accessor(models.Entity.fromString(userId, EntityType.withName(userType))), contentType, id)
        } yield {
          for {  accessor <- userOrErr.right; perms <- permsOrErr.right } yield {
            f(item)(Accessor(accessor))(perms.copy(user=Accessor(accessor)))(user)(request)
          }
        }
      }
    }
  }

  def setItemPermissionsPostAction(id: String, userType: String, userId: String)(
      f: acl.ItemPermissionSet[Accessor] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        val data = request.body.asFormUrlEncoded.getOrElse(Map())
        val perms: List[String] = data.get(contentType.toString).map(_.toList).getOrElse(List())

        AsyncRest {
          for {
            accessorOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
          } yield {
            for { accessor <- accessorOrErr.right} yield {
              AsyncRest {
                rest.PermissionDAO(user).setItem(Accessor(accessor), contentType, id, perms).map { permsOrErr =>
                  permsOrErr.right.map { perms =>
                    f(perms)(user)(request)
                  }
                }
              }
            }
          }
        }
    }
  }
}

