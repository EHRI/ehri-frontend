package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import defines.PermissionType
import models.UserProfile
import play.api.i18n.Messages

/**
 * Controller trait for deleting AccessibleEntities.
 *
 * @tparam T the Entity's built representation
 */
trait EntityDelete[T <: AccessibleEntity] extends EntityRead[T] {

  type DeleteViewType = (T, Call, Call, UserProfile, RequestHeader) => play.api.templates.Html
  val deleteAction: String => Call
  val deleteView: DeleteViewType
  val cancelAction: String => Call

  def delete(id: String) = withItemPermission(id, PermissionType.Delete, contentType) { implicit user =>
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

  def deletePost(id: String) = withItemPermission(id, PermissionType.Delete, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).delete(id).map { boolOrErr =>
          boolOrErr.right.map {
            ok => Redirect(controllers.routes.Application.index).flashing("success" -> Messages("confirmations.itemWasDeleted", id))
          }
        }
      }
  }
}