package controllers.base

import defines.ContentType
import acl.GlobalPermissionSet
import models.base.Persistable
import models.base.Accessor
import play.api.mvc.Call
import play.api.mvc.RequestHeader
import defines.PermissionType
import models.UserProfile

import play.api.libs.concurrent.execution.defaultContext

trait PermissionsController[F <: Persistable, T <: Accessor] extends EntityRead[T] {

  val permsAction: String => Call
  val setPermsAction: String => Call
  type PermViewType = (Accessor, GlobalPermissionSet[Accessor], Call, UserProfile, RequestHeader) => play.api.templates.Html
  val permView: PermViewType

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