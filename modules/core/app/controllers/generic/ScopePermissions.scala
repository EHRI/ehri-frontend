package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import utils.{Page, PageParams}
import backend.{BackendReadable, BackendContentType}

/**
 * Trait for setting visibility on any AccessibleEntity.
 */
trait ScopePermissions[MT] extends ItemPermissions[MT] {

  val targetContentTypes: Seq[ContentTypes.Value]

  def manageScopedPermissionsAction(id: String)(
      f: MT => Page[PermissionGrant] => Page[PermissionGrant]=> Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val itemParams = PageParams.fromRequest(request)
      val scopeParams = PageParams.fromRequest(request, namespace = "s")
      for {
        permGrants <- backend.listItemPermissionGrants(id, itemParams)
        scopeGrants <- backend.listScopePermissionGrants(id, scopeParams)
      } yield f(item)(permGrants)(scopeGrants)(userOpt)(request)
    }
  }

  def setScopedPermissionsAction(id: String, userType: EntityType.Value, userId: String)(
      f: MT => Accessor => acl.GlobalPermissionSet => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      for {
        accessor <- backend.get[Accessor](Accessor.resourceFor(userType), userId)
        perms <- backend.getScopePermissions(userId, id)
      } yield f(item)(accessor)(perms)(userOpt)(request)
    }
  }

  def setScopedPermissionsPostAction(id: String, userType: EntityType.Value, userId: String)(
      f: acl.GlobalPermissionSet => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap

      for {
        accessor <- backend.get[Accessor](Accessor.resourceFor(userType), userId)
        perms <- backend.setScopePermissions(userId, id, perms)
      } yield f(perms)(userOpt)(request)
    }
  }
}

