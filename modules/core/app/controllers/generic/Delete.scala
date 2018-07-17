package controllers.generic

import defines.{EventType, PermissionType}
import models.base.{MetaModel, Model}
import play.api.mvc._
import services.data.ContentType

import scala.concurrent.Future

/**
  * Controller trait for deleting [[models.base.Accessible]] items.
  */
trait Delete[MT <: MetaModel] extends Write {

  self: Read[MT] =>

  protected def CheckDeleteAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Delete)

  private[generic] def DeleteTransformer(id: String)(implicit ct: ContentType[MT]) =
    new CoreActionTransformer[ItemPermissionRequest, OptionalUserRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[OptionalUserRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        for {
          pre <- itemLifecycle.preSave(Some(id), req.item.model, EventType.deletion)
          _ <- userDataApi.delete(id, logMsg = getLogMessage)
          post <- itemLifecycle.postSave(Some(id), req.item, pre, EventType.deletion)
        } yield OptionalUserRequest(request.userOpt, request)
      }
    }

  protected def DeleteAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[OptionalUserRequest, AnyContent] =
    WithItemPermissionAction(id, PermissionType.Delete) andThen DeleteTransformer(id)
}