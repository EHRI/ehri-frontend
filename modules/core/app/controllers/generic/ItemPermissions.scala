package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import utils.{Page, PageParams}
import backend.{BackendReadable, BackendContentType}

/**
 * Trait for setting permissions on an individual item.
 */
trait ItemPermissions[MT] extends Read[MT] {

  def manageItemPermissionsAction(id: String)(
      f: MT => Page[PermissionGrant] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      backend.listItemPermissionGrants(id, params).map { permGrants =>
        f(item)(permGrants)(userOpt)(request)
      }
    }
  }

  def addItemPermissionsAction(id: String)(
      f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(item)(users)(groups)(userOpt)(request)
      }
    }
  }


  def setItemPermissionsAction(id: String, userType: EntityType.Value, userId: String)(
      f: MT => Accessor => acl.ItemPermissionSet => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      for {
        accessor <- backend.get[Accessor](Accessor.resourceFor(userType), userId)
        perms <- backend.getItemPermissions(userId, ct.contentType, id)
      } yield f(item)(accessor)(perms)(userOpt)(request)
    }
  }

  def setItemPermissionsPostAction(id: String, userType: EntityType.Value, userId: String)(
      f: acl.ItemPermissionSet => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val perms: List[String] = data.get(ct.contentType.toString).map(_.toList).getOrElse(List())
      for {
        accessor <- backend.get[Accessor](Accessor.resourceFor(userType), userId)
        perms <- backend.setItemPermissions(userId, ct.contentType, id, perms)
      } yield f(perms)(userOpt)(request)
    }
  }
}

