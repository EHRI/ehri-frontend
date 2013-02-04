package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import defines.PermissionType
import models.{Entity, UserProfile}

/**
 * Controller trait for deleting AccessibleEntities.
 *
 * @tparam T the Entity's built representation
 */
trait EntityDelete[T <: AccessibleEntity] extends EntityRead[T] {

  def deleteAction(id: String)(f: Entity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def deletePostAction(id: String)(f: Boolean => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, userOpt).delete(id).map { boolOrErr =>
          boolOrErr.right.map { ok =>
            f(ok)(userOpt)(request)
          }
        }
      }
    }
  }
}