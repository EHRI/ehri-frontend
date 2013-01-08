package controllers.base

import defines.ContentType
import acl.GlobalPermissionSet
import models.base.Persistable
import models.base.Accessor
import play.api.mvc.Call
import play.api.mvc.RequestHeader
import defines.PermissionType
import models.{PermissionGrant, UserProfile}

import play.api.libs.concurrent.execution.defaultContext

/**
 * Trait for managing permissions on Accessor models that can have permissions assigned to them.
 *
 * @tparam F
 * @tparam T
 */
trait PermissionHolderController[F <: Persistable, T <: Accessor] extends EntityRead[T] {

  val permsAction: String => Call
  val setPermsAction: String => Call

  // Type and stub for the view function
  type PermViewType = (Accessor, GlobalPermissionSet[Accessor], Call, UserProfile, RequestHeader) => play.api.templates.Html
  val permView: PermViewType

  // List view function
  type PermListViewType = (Accessor, rest.Page[PermissionGrant], UserProfile, RequestHeader) => play.api.templates.Html
  val permListView: PermListViewType

  /**
   * Display a list of permissions that have been granted to the given accessor.
   * @param id
   * @param page
   * @param limit
   * @return
   */
  def grantList(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          // NB: to save having to wait we just fake the permission user here.
          permsOrErr <- rest.PermissionDAO(user).list(builder(models.Entity.fromString(id, entityType)), page, limit)
        } yield {
          for { perms <- permsOrErr.right; item <- itemOrErr.right} yield {
            Ok(permListView(builder(item), perms, user, request))
          }
        }
      }
  }


  def permissions(id: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          permsOrErr <- rest.PermissionDAO(user).get(builder(itemOrErr.right.get))
        } yield {
          for { perms <- permsOrErr.right; item <- itemOrErr.right} yield {
            Ok(permView(builder(item), perms, setPermsAction(id), user, request))
          }
        }
      }
  }

  def permissionsPost(id: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
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
          newpermsOrErr.right.map { perms =>
            Redirect(showAction(id))
          }
        }
      }
  }
}