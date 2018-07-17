package controllers.generic

import defines.{EventType, PermissionType}
import models.UserProfile
import models.base._
import play.api.data.Form
import play.api.mvc._
import services.data.{ContentType, ValidationError, Writable}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
  * Controller trait which updates an AccessibleEntity.
  */
trait Update[MT <: Model{type T <: ModelData with Persistable}] extends Write {

  this: Read[MT] =>

  case class UpdateRequest[A](
    item: MT,
    formOrItem: Either[Form[MT#T], MT],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def EditAction(itemId: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(itemId, PermissionType.Update)

  protected def UpdateAction(id: String, form: Form[MT#T], transformer: MT#T => Future[MT#T] = Future.successful)(
    implicit ct: ContentType[MT], wd: Writable[MT#T]): ActionBuilder[UpdateRequest, AnyContent] =
    EditAction(id) andThen new CoreActionTransformer[ItemPermissionRequest, UpdateRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[UpdateRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        form.bindFromRequest.fold(
          errorForm => immediate(UpdateRequest(request.item, Left(errorForm), request.userOpt, request.request)),
          mod => {
            (for {
              pre <- itemLifecycle.preSave(Some(id), mod, EventType.modification)
              transformed <- transformer.apply(pre)
              saved <- userDataApi.update[MT, MT#T](id, transformed, logMsg = getLogMessage)
              post <- itemLifecycle.postSave(Some(id), saved, pre, EventType.modification)
            } yield UpdateRequest(request.item, Right(post), request.userOpt, request)) recover {
              case ValidationError(errorSet) =>
                val filledForm = mod.getFormErrors(errorSet, form.fill(mod))
                UpdateRequest(request.item, Left(filledForm), request.userOpt, request)
            }
          }
        )
      }
    }
}
