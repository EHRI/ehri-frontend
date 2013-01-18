package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import defines.PermissionType
import models.{Entity, UserProfile}
import play.api.i18n.Messages

/**
 * Controller trait for deleting AccessibleEntities.
 *
 * @tparam T the Entity's built representation
 */
trait EntityDelete[T <: AccessibleEntity] extends EntityRead[T] {

  def deleteAction(id: String)(f: Entity => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Delete, contentType) { implicit user =>
      implicit request =>
        getEntity(id, Some(user)) { item =>
          f(item)(user)(request)
        }
    }
  }

  def deletePostAction(id: String)(f: Boolean => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Delete, contentType) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        AsyncRest {
          rest.EntityDAO(entityType, maybeUser).delete(id).map { boolOrErr =>
            boolOrErr.right.map { ok =>
              f(ok)(user)(request)
            }
          }
        }
    }
  }
}