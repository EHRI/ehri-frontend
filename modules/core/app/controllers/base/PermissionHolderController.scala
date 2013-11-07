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

  type GlobalPermissionCallback = MT => GlobalPermissionSet[MT] => Option[UserProfile] => Request[AnyContent] => Result

  implicit object PermissionGrantResource extends RestResource[PermissionGrant] {
    val entityType = EntityType.PermissionGrant
  }

  /**
   * Display a list of permissions that have been granted to the given accessor.
   * @param id
   * @return
   */
  def grantListAction(id: String)(
      f: MT => rest.Page[PermissionGrant] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        val params = PageParams.fromRequest(request)
        for {
          // NB: to save having to wait we just fake the permission user here.
          permsOrErr <- backend.listPermissionGrants(item, params)
        } yield {
          for { perms <- permsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }


  def setGlobalPermissionsAction(id: String)(f: GlobalPermissionCallback)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          permsOrErr <- backend.getGlobalPermissions(item)
        } yield {
          for { perms <- permsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }

  def setGlobalPermissionsPostAction(id: String)(f: GlobalPermissionCallback)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = ContentTypes.values.toList.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap
      AsyncRest {
        for {
          newpermsOrErr <- backend.setGlobalPermissions(item, perms)
        } yield {
          for { perms <- newpermsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }

  def revokePermissionAction(id: String, permId: String)(
      f: MT => PermissionGrant => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          permOrErr <- backend.get[PermissionGrant](permId)
        } yield {
          for { perm <- permOrErr.right } yield {
            f(item)(perm)(userOpt)(request)
          }
        }
      }
    }
  }

  def revokePermissionActionPost(id: String, permId: String)(
      f: MT => Boolean => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          boolOrErr <- backend.delete[PermissionGrant](permId)
        } yield {
          for { bool <- boolOrErr.right } yield {
            f(item)(bool)(userOpt)(request)
          }
        }
      }
    }
  }
}