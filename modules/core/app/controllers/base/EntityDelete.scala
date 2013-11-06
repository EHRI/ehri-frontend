package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AnyModel
import defines.PermissionType
import models.UserProfile
import play.api.libs.json.Json
import models.json.RestReadable

/**
 * Controller trait for deleting AccessibleEntities.
 *
 * @tparam MT the Entity's meta representation
 */
trait EntityDelete[MT] extends EntityRead[MT] {

  def deleteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def deletePostAction(id: String)(f: Boolean => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      rest.EntityDAO(entityType).delete(id, logMsg = getLogMessage).map { ok =>
        request match {
          case Accepts.Html() => f(ok)(userOpt)(request)
          case Accepts.Json() => Ok(Json.toJson(ok))
        }
      }
    }
  }
}