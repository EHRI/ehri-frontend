package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.UserProfile
import play.api.libs.json.Json
import models.json.{RestResource, RestReadable}

/**
 * Controller trait for deleting AccessibleEntities.
 */
trait Delete[MT] extends Generic[MT] {

  def deleteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def deletePostAction(id: String)(f: Boolean => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Delete, contentType) { item => implicit userOpt => implicit request =>
      backend.delete[MT](id, logMsg = getLogMessage).map { ok =>
        f(ok)(userOpt)(request)
      }
    }
  }
}