package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import defines.PermissionType
import models.{Entity, UserProfile}
import play.api.libs.json.Json

/**
 * Controller trait for deleting AccessibleEntities.
 *
 * @tparam T the Entity's built representation
 */
trait EntityDelete[T <: AccessibleEntity] extends EntityRead[T] {

  def deleteAction(id: String)(f: Entity => Option[UserProfileMeta] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def deletePostAction(id: String)(f: Boolean => Option[UserProfileMeta] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, userOpt).delete(id, logMsg = getLogMessage).map { boolOrErr =>
          boolOrErr.right.map { ok =>
            request match {
              case Accepts.Html() => f(ok)(userOpt)(request)
              case Accepts.Json() => Ok(Json.toJson(ok))
            }
          }
        }
      }
    }
  }
}