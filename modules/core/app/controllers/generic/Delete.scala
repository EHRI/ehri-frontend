package controllers.generic

import defines.PermissionType
import play.api.mvc._
import services.data.ContentType

import scala.concurrent.Future

/**
  * Controller trait for deleting [[models.base.Accessible]] items.
  */
trait Delete[MT] extends Write {

  self: Read[MT] =>

  protected def CheckDeleteAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Delete)

  private[generic] def DeleteTransformer(id: String)(implicit ct: ContentType[MT]) =
    new CoreActionTransformer[ItemPermissionRequest, OptionalUserRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[OptionalUserRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        userDataApi.delete(id, logMsg = getLogMessage).map { _ =>
          OptionalUserRequest(request.userOpt, request)
        }
      }
    }

  protected def DeleteAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[OptionalUserRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Delete) andThen DeleteTransformer(id)
}