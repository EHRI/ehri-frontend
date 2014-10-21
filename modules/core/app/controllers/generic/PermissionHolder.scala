package controllers.generic

import defines.{ContentTypes, PermissionType}
import acl.GlobalPermissionSet
import models.base.Accessor
import play.api.mvc._
import models._

import play.api.libs.concurrent.Execution.Implicits._
import utils.{Page, PageParams}
import backend.{BackendReadable, BackendContentType}

/**
 * Trait for managing permissions on Accessor models that can have permissions assigned to them.
 */
trait PermissionHolder[MT <: Accessor] extends Read[MT] {

  type GlobalPermissionCallback = MT => GlobalPermissionSet => Option[UserProfile] => Request[AnyContent] => Result

  /**
   * Display a list of permissions that have been granted to the given accessor.
   */
  def grantListAction(id: String)(
      f: MT => Page[PermissionGrant] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      backend.listPermissionGrants[PermissionGrant](item.id, params).map { perms =>
        f(item)(perms)(userOpt)(request)
      }
    }
  }


  def setGlobalPermissionsAction(id: String)(f: GlobalPermissionCallback)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      backend.getGlobalPermissions(item.id).map { perms =>
        f(item)(perms)(userOpt)(request)
      }
    }
  }

  def setGlobalPermissionsPostAction(id: String)(f: GlobalPermissionCallback)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val perms: Map[String, List[String]] = ContentTypes.values.toList.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap
      backend.setGlobalPermissions(item.id, perms).map { perms =>
        f(item)(perms)(userOpt)(request)
      }
    }
  }

  def revokePermissionAction(id: String, permId: String)(
      f: MT => PermissionGrant => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      backend.get[PermissionGrant](permId).map { perm =>
        f(item)(perm)(userOpt)(request)
      }
    }
  }

  def revokePermissionActionPost(id: String, permId: String)(
      f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      backend.delete(permId).map { ok =>
        f(item)(userOpt)(request)
      }
    }
  }
}
