package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import models.json.RestReadable
import utils.ListParams
import utils.ListParams
import scala.concurrent.Await
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam MT the entity's build class
 */
trait PermissionScopeController[MT] extends PermissionItemController[MT] {

  val targetContentTypes: Seq[ContentTypes.Value]

  def manageScopedPermissionsAction(id: String)(
      f: MT => rest.Page[PermissionGrant] => rest.Page[PermissionGrant]=> Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val itemParams = ListParams.fromRequest(request)
      val scopeParams = ListParams.fromRequest(request, namespace = "s")
      for {
        permGrants <- rest.PermissionDAO(userOpt).listForItem(id, itemParams)
        scopeGrants <- rest.PermissionDAO(userOpt).listForScope(id, scopeParams)
      } yield f(item)(permGrants)(scopeGrants)(userOpt)(request)
    }
  }

  def setScopedPermissionsAction(id: String, userType: String, userId: String)(
      f: MT => Accessor => acl.GlobalPermissionSet[Accessor] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      for {
        accessor <- rest.EntityDAO[Accessor](EntityType.withName(userType), userOpt).get(userId)
        // NB: Faking user for fetching perms to avoid blocking.
        // This means that when we have both the perm set and the user
        // we need to re-assemble them so that the permission set has
        // access to a user's groups to understand inheritance.
        perms <- rest.PermissionDAO(userOpt).getScope(accessor, id)
      } yield {
        f(item)(accessor)(perms.copy(user=accessor))(userOpt)(request)
      }
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
        accessor <- rest.EntityDAO[Accessor](EntityType.withName(userType), userOpt).get(userId)
        sperms <- rest.PermissionDAO(userOpt).setScope(accessor, id, perms)
      } yield f(sperms)(userOpt)(request)
    }
  }
}

