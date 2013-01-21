package controllers.base

import defines.ContentType
import acl.GlobalPermissionSet
import models.base.Persistable
import models.base.Accessor
import play.api.mvc._
import defines.PermissionType
import models.{Entity, PermissionGrant, UserProfile}

import play.api.libs.concurrent.Execution.Implicits._

/**
 * Trait for managing permissions on Accessor models that can have permissions assigned to them.
 *
 * @tparam T
 */
trait PermissionHolderController[T <: Accessor] extends EntityRead[T] {

  /**
   * Display a list of permissions that have been granted to the given accessor.
   * @param id
   * @param page
   * @param limit
   * @return
   */
  def grantListAction(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT)(f: Entity => rest.Page[PermissionGrant] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
            // NB: to save having to wait we just fake the permission user here.
            permsOrErr <- rest.PermissionDAO(user).list(builder(models.Entity.fromString(id, entityType)), page, limit)
          } yield {
            for { perms <- permsOrErr.right; item <- itemOrErr.right} yield {
              f(item)(perms)(user)(request)
            }
          }
        }
    }
  }


  def setGlobalPermissionsAction(id: String)(f: Entity => GlobalPermissionSet[T] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
            permsOrErr <- rest.PermissionDAO(user).get(builder(itemOrErr.right.get))
          } yield {
            for { perms <- permsOrErr.right; item <- itemOrErr.right} yield {
              f(item)(perms)(user)(request)
            }
          }
        }
    }
  }

  def setGlobalPermissionsPostAction(id: String)(f: Entity => GlobalPermissionSet[T] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
      implicit request =>
        val data = request.body.asFormUrlEncoded.getOrElse(Map())
        val perms: Map[String, List[String]] = ContentType.values.toList.map { ct =>
          (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
        }.toMap
        implicit val maybeUser = Some(user)
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
            newpermsOrErr <- rest.PermissionDAO(user).set(builder(itemOrErr.right.get), perms)
          } yield {
            for { perms <- newpermsOrErr.right; item <- itemOrErr.right} yield {
              f(item)(perms)(user)(request)
            }
          }
        }
    }
  }
}