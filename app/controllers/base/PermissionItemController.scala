package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models.{PermissionGrant, Entity, UserProfile}

/**
 * Trait for setting permissions on an individual item.
 *
 * @tparam T the entity's build class
 */
trait PermissionItemController[T <: AccessibleEntity] extends EntityRead[T] {

  def manageItemPermissionsAction(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT)(
      f: Entity => rest.Page[PermissionGrant] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          permGrantsOrErr <- rest.PermissionDAO(userOpt).listForItem(id, math.max(page, 1), math.max(limit, 1))
        } yield {
          for { permGrants <- permGrantsOrErr.right } yield {
            f(item)(permGrants)(userOpt)(request)
          }
        }
      }
    }
  }

  def addItemPermissionsAction(id: String)(f: Entity => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, userOpt).get(id)
          users <- rest.RestHelpers.getUserList
          groups <- rest.RestHelpers.getGroupList
        } yield {
          for { item <- itemOrErr.right } yield {
            f(item)(users)(groups)(userOpt)(request)
          }
        }
      }
    }
  }


  def setItemPermissionsAction(id: String, userType: String, userId: String)(
      f: Entity => Accessor => acl.ItemPermissionSet[Accessor] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          userOrErr <- rest.EntityDAO(EntityType.withName(userType), userOpt).get(userId)
          // FIXME: Faking user for fetching perms to avoid blocking.
          // This means that when we have both the perm set and the userOpt
          // we need to re-assemble them so that the permission set has
          // access to a userOpt's groups to understand inheritance.
          permsOrErr <- rest.PermissionDAO(userOpt)
              .getItem(Accessor(models.Entity.fromString(userId, EntityType.withName(userType))), contentType, id)
        } yield {
          for {  accessor <- userOrErr.right; perms <- permsOrErr.right } yield {
            f(item)(Accessor(accessor))(perms.copy(user=Accessor(accessor)))(userOpt)(request)
          }
        }
      }
    }
  }

  def setItemPermissionsPostAction(id: String, userType: String, userId: String)(
      f: acl.ItemPermissionSet[Accessor] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: List[String] = data.get(contentType.toString).map(_.toList).getOrElse(List())
      getEntity(EntityType.withName(userType), userId) { accessor =>
        AsyncRest {
          rest.PermissionDAO(userOpt).setItem(Accessor(accessor), contentType, id, perms).map { permsOrErr =>
            permsOrErr.right.map { perms =>
              f(perms)(userOpt)(request)
            }
          }
        }
      }
    }
  }
}

