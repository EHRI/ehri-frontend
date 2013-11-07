package controllers.base

import defines.{EntityType, ContentTypes, PermissionType}
import acl.GlobalPermissionSet
import models.base.Accessor
import play.api.mvc._
import models._

import play.api.libs.concurrent.Execution.Implicits._
import models.json.{RestResource, RestReadable}
import utils.PageParams

/**
 * Trait for managing permissions on Accessor models that can have permissions assigned to them.
 *
 * @tparam MT
 */
trait PermissionHolderController[MT <: Accessor] extends EntityRead[MT] {

  type GlobalPermissionCallback = MT => GlobalPermissionSet[MT] => Option[UserProfile] => Request[AnyContent] => SimpleResult

  implicit object PermissionGrantResource extends RestResource[PermissionGrant] {
    val entityType = EntityType.PermissionGrant
  }

  /**
   * Display a list of permissions that have been granted to the given accessor.
   * @param id
   * @return
   */
  def grantListAction(id: String)(
      f: MT => rest.Page[PermissionGrant] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      backend.listPermissionGrants(item, params).map { perms =>
        f(item)(perms)(userOpt)(request)
      }
    }
  }


  def setGlobalPermissionsAction(id: String)(f: GlobalPermissionCallback)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      backend.getGlobalPermissions(item).map { perms =>
        f(item)(perms)(userOpt)(request)
      }
    }
  }

  def setGlobalPermissionsPostAction(id: String)(f: GlobalPermissionCallback)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = ContentTypes.values.toList.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap
      backend.setGlobalPermissions(item, perms).map { perms =>
        f(item)(perms)(userOpt)(request)
      }
    }
  }

  def revokePermissionAction(id: String, permId: String)(
      f: MT => PermissionGrant => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      backend.get[PermissionGrant](permId).map { perm =>
        f(item)(perm)(userOpt)(request)
      }
    }
  }

  def revokePermissionActionPost(id: String, permId: String)(
      f: MT => Boolean => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      backend.delete(permId).map { ok =>
        f(item)(ok)(userOpt)(request)
      }
    }
  }
}
