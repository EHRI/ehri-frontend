package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.UserProfile
import backend.{BackendReadable, BackendContentType, BackendResource}

/**
 * Controller trait for deleting AccessibleEntities.
 */
trait Delete[MT] extends Generic[MT] {

  def deleteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Delete) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def deletePostAction(id: String)(f: Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Delete) { item => implicit userOpt => implicit request =>
      backend.delete[MT](id, logMsg = getLogMessage).map { _ =>
        f(userOpt)(request)
      }
    }
  }
}