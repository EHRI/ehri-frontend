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

  val builder: Entity => T

  /**
   * Display a list of permissions that have been granted to the given accessor.
   * @param id
   * @param page
   * @param limit
   * @return
   */
  def grantListAction(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT)(f: Entity => rest.Page[PermissionGrant] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          // NB: to save having to wait we just fake the permission user here.
          permsOrErr <- rest.PermissionDAO(userOpt).list(builder(models.Entity.fromString(id, entityType)), page, limit)
        } yield {
          for { perms <- permsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }


  def setGlobalPermissionsAction(id: String)(f: Entity => GlobalPermissionSet[T] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          permsOrErr <- rest.PermissionDAO(userOpt).get(builder(item))
        } yield {
          for { perms <- permsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }

  def setGlobalPermissionsPostAction(id: String)(f: Entity => GlobalPermissionSet[T] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = ContentType.values.toList.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap
      AsyncRest {
        for {
          newpermsOrErr <- rest.PermissionDAO(userOpt).set(builder(item), perms)
        } yield {
          for { perms <- newpermsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }
}