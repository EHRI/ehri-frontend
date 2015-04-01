package controllers.generic

import backend.ContentType
import defines.PermissionType
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * Controller trait for deleting AccessibleEntities.
 */
trait Delete[MT] extends Generic {

  self: Read[MT] =>

  protected def CheckDeleteAction(id: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Delete)

  private[generic] def DeleteTransformer(id: String)(implicit ct: ContentType[MT]) = new ActionTransformer[ItemPermissionRequest,OptionalUserRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[OptionalUserRequest[A]] = {
      implicit val req = request
      userBackend.delete(id, logMsg = getLogMessage).map { _ =>
        OptionalUserRequest(request.userOpt, request)
      }
    }
  }

  protected def DeleteAction(id: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Delete) andThen DeleteTransformer(id)
}