package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models.{PermissionGrantMeta, UserProfileMeta}
import models.json.RestReadable

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam MT the entity's build class
 */
trait PermissionScopeController[MT] extends PermissionItemController[MT] {

  val targetContentTypes: Seq[ContentType.Value]

  def manageScopedPermissionsAction(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT)(
      f: MT => rest.Page[PermissionGrantMeta] => rest.Page[PermissionGrantMeta]=> Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          permGrantsOrErr <- rest.PermissionDAO(userOpt).listForItem(id, math.max(page, 1), math.max(limit, 1))
          scopeGrantsOrErr <- rest.PermissionDAO(userOpt).listForScope(id, math.max(spage, 1), math.max(limit, 1))
        } yield {
          for { permGrants <- permGrantsOrErr.right ; scopeGrants <- scopeGrantsOrErr.right } yield {
            f(item)(permGrants)(scopeGrants)(userOpt)(request)
          }
        }
      }
    }
  }

  def setScopedPermissionsAction(id: String, userType: String, userId: String)(
      f: MT => Accessor => acl.GlobalPermissionSet[Accessor] => Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt =>
      implicit request =>
        AsyncRest {
          for {
            userOrErr <- rest.EntityDAO[Accessor](EntityType.withName(userType), userOpt).get(userId)
            // FIXME: Faking user for fetching perms to avoid blocking.
            // This means that when we have both the perm set and the user
            // we need to re-assemble them so that the permission set has
            // access to a user's groups to understand inheritance.
            permsOrErr <- rest.PermissionDAO(userOpt).getScope(userOrErr.right.get, id)
          } yield {
            for { accessor <- userOrErr.right; perms <- permsOrErr.right } yield {
              f(item)(accessor)(perms.copy(user=accessor))(userOpt)(request)
            }
          }
        }
    }
  }

  def setScopedPermissionsPostAction(id: String, userType: String, userId: String)(
      f: acl.GlobalPermissionSet[Accessor] => Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap

      AsyncRest {
        for {
          accessorOrErr <- rest.EntityDAO[Accessor](EntityType.withName(userType), userOpt).get(userId)
        } yield {
          for { accessor <- accessorOrErr.right} yield {
            AsyncRest {
              rest.PermissionDAO(userOpt).setScope(accessor, id, perms).map { permsOrErr =>
                permsOrErr.right.map { perms =>
                  f(perms)(userOpt)(request)
                }
              }
            }
          }
        }
      }
    }
  }
}

