package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models.{PermissionGrant, Entity, UserProfile}
import acl.GlobalPermissionSet

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait PermissionScopeController[T <: AccessibleEntity] extends PermissionItemController[T] {

  val targetContentTypes: Seq[ContentType.Value]

  def manageScopedPermissionsAction(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT)(
      f: Entity => rest.Page[PermissionGrant] => rest.Page[PermissionGrant]=> UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
      implicit request =>

        implicit val maybeUser = Some(user)
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
            permGrantsOrErr <- rest.PermissionDAO(user).listForItem(id, math.max(page, 1), math.max(limit, 1))
            scopeGrantsOrErr <- rest.PermissionDAO(user).listForScope(id, math.max(spage, 1), math.max(limit, 1))
          } yield {
            for { item <- itemOrErr.right ; permGrants <- permGrantsOrErr.right ; scopeGrants <- scopeGrantsOrErr.right } yield {
              f(item)(permGrants)(scopeGrants)(user)(request)
            }
          }
        }
    }
  }

  def setScopedPermissionsAction(id: String, userType: String, userId: String)(
      f: Entity => Accessor => acl.GlobalPermissionSet[Accessor] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
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
              f(item)(Accessor(accessor))(perms.copy(user=Accessor(accessor)))(user)(request)
            }
          }
        }
    }
  }

  def setScopedPermissionsPostAction(id: String, userType: String, userId: String)(
      f: acl.GlobalPermissionSet[Accessor] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        val data = request.body.asFormUrlEncoded.getOrElse(Map())
        val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
          (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
        }.toMap

        AsyncRest {
          for {
            accessorOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
          } yield {
            for { accessor <- accessorOrErr.right} yield {
              AsyncRest {
                rest.PermissionDAO(user).setScope(Accessor(accessor), id, perms).map { permsOrErr =>
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

