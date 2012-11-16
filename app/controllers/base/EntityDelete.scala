package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call

trait EntityDelete[T <: AccessibleEntity] extends EntityRead[T] {

  type DeleteViewType = (T, Call, Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val deleteAction: String => Call
  val deleteView: DeleteViewType
  val cancelAction: String => Call

  def delete(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: T = builder(item)
            Ok(deleteView(doc, deleteAction(id), cancelAction(id), maybeUser, request))

          }
        }
      }
  }

  def deletePost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).delete(id).map { boolOrErr =>
          boolOrErr.right.map { ok => Redirect(listAction(0, 20)) }
        }
      }
  }
}