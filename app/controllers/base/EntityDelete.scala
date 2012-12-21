package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call
import defines.PermissionType
import models.UserProfileRepr

trait EntityDelete[T <: AccessibleEntity] extends EntityRead[T] {

  type DeleteViewType = (T, Call, Call, UserProfileRepr, RequestHeader) => play.api.templates.Html
  val deleteAction: String => Call
  val deleteView: DeleteViewType
  val cancelAction: String => Call

  def delete(id: String) = withItemPermission(id, PermissionType.Delete) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: T = builder(item)
            Ok(deleteView(doc, deleteAction(id), cancelAction(id), user, request))

          }
        }
      }
  }

  def deletePost(id: String) = withItemPermission(id, PermissionType.Delete) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).delete(id).map { boolOrErr =>
          boolOrErr.right.map { ok => Redirect(listAction(0, DEFAULT_LIMIT)) }
        }
      }
  }
}