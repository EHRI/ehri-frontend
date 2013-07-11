package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AnyModel
import defines.PermissionType
import models.UserProfileMeta
import play.api.libs.json.Json
import models.json.RestReadable

/**
 * Controller trait for deleting AccessibleEntities.
 *
 * @tparam MT the Entity's meta representation
 */
trait EntityDelete[MT] extends EntityRead[MT] {

  def deleteAction(id: String)(f: MT => Option[UserProfileMeta] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def deletePostAction(id: String)(f: Boolean => Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
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