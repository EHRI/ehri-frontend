package controllers.generic

import backend.{BackendContentType, BackendReadable}
import defines.PermissionType
import models.UserProfile
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * Controller trait for deleting AccessibleEntities.
 */
trait Delete[MT] extends Generic[MT] {

  self: Read[MT] =>

  protected def CheckDeleteAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Delete)

  private[generic] def DeleteTransformer(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = new ActionTransformer[ItemPermissionRequest,OptionalProfileRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[OptionalProfileRequest[A]] = {
      implicit val req = request
      backend.delete(id, logMsg = getLogMessage).map { _ =>
        OptionalProfileRequest(request.profileOpt, request)
      }
    }
  }

  protected def DeleteAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Delete) andThen DeleteTransformer(id)

  @deprecated(message = "Use CheckDeleteAction instead", since = "1.0.2")
  def deleteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Delete) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  @deprecated(message = "Use DeleteAction instead", since = "1.0.2")
  def deletePostAction(id: String)(f: Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Delete) { item => implicit userOpt => implicit request =>
      backend.delete[MT](id, logMsg = getLogMessage).map { _ =>
        f(userOpt)(request)
      }
    }
  }
}