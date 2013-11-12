package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import models.json.RestReadable
import utils.PageParams
import backend.Page

/**
 * Trait for setting visibility on any AccessibleEntity.
 */
trait ScopePermissions[MT] extends ItemPermissions[MT] {

  val targetContentTypes: Seq[ContentTypes.Value]

  def manageScopedPermissionsAction(id: String)(
      f: MT => Page[PermissionGrant] => Page[PermissionGrant]=> Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val itemParams = PageParams.fromRequest(request)
      val scopeParams = PageParams.fromRequest(request, namespace = "s")
      for {
        permGrants <- backend.listItemPermissionGrants(id, itemParams)
        scopeGrants <- backend.listScopePermissionGrants(id, scopeParams)
      } yield f(item)(permGrants)(scopeGrants)(userOpt)(request)
    }
  }

  def setScopedPermissionsAction(id: String, userType: String, userId: String)(
      f: MT => Accessor => acl.GlobalPermissionSet[Accessor] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      for {
        accessor <- backend.get[Accessor](EntityType.withName(userType), userId)
        perms <- backend.getScopePermissions(accessor, id)
      } yield f(item)(accessor)(perms.copy(user=accessor))(userOpt)(request)
    }
  }

  def setScopedPermissionsPostAction(id: String, userType: String, userId: String)(
      f: acl.GlobalPermissionSet[Accessor] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap

      for {
        accessor <- backend.get[Accessor](EntityType.withName(userType), userId)
        perms <- backend.setScopePermissions(accessor, id, perms)
      } yield f(perms)(userOpt)(request)
    }
  }
}

