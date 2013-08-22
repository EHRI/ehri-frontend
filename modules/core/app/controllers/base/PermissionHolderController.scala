package controllers.base

import defines.{EntityType, ContentTypes, PermissionType}
import acl.GlobalPermissionSet
import models.base.Accessor
import play.api.mvc._
import models._

import play.api.libs.concurrent.Execution.Implicits._
import models.json.RestReadable
import utils.ListParams
import utils.ListParams

/**
 * Trait for managing permissions on Accessor models that can have permissions assigned to them.
 *
 * @tparam MT
 */
trait PermissionHolderController[MT <: Accessor] extends EntityRead[MT] {

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
        val params = ListParams.fromRequest(request)
        for {
          // NB: to save having to wait we just fake the permission user here.
          permsOrErr <- rest.PermissionDAO(userOpt).list(item, params)
        } yield {
          for { perms <- permsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }


  def setGlobalPermissionsAction(id: String)(
      f: MT => GlobalPermissionSet[MT] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          permsOrErr <- rest.PermissionDAO(userOpt).get(item)
        } yield {
          for { perms <- permsOrErr.right } yield {
            f(item)(perms)(userOpt)(request)
          }
        }
      }
    }
  }

  def setGlobalPermissionsPostAction(id: String)(
      f: MT => GlobalPermissionSet[MT] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = ContentTypes.values.toList.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap
      AsyncRest {
        for {
          newpermsOrErr <- rest.PermissionDAO(userOpt).set(item, perms)
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
          permOrErr <- rest.EntityDAO[PermissionGrant](EntityType.PermissionGrant, userOpt).get(permId)
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
          boolOrErr <- rest.EntityDAO[PermissionGrant](EntityType.PermissionGrant, userOpt).delete(permId)
        } yield {
          for { bool <- boolOrErr.right } yield {
            f(item)(bool)(userOpt)(request)
          }
        }
      }
    }
  }
}