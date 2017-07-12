package controllers.generic

import backend.rest.ValidationError
import backend.{ContentType, Writable}
import defines.PermissionType
import models.UserProfile
import models.base._
import play.api.data.Form
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
  * Controller trait which updates an AccessibleEntity.
  */
trait Update[F <: Model with Persistable, MT <: MetaModel[F]] extends Write {

  this: Read[MT] =>

  case class UpdateRequest[A](
    item: MT,
    formOrItem: Either[Form[F], MT],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def EditAction(itemId: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest, AnyContent] =
    WithItemPermissionAction(itemId, PermissionType.Update)

  protected def UpdateAction(id: String, form: Form[F], transformer: F => F = identity)(
    implicit ct: ContentType[MT], wd: Writable[F]): ActionBuilder[UpdateRequest, AnyContent] =
    EditAction(id) andThen new CoreActionTransformer[ItemPermissionRequest, UpdateRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[UpdateRequest[A]] = {
        implicit val req = request
        form.bindFromRequest.fold(
          errorForm => immediate(UpdateRequest(request.item, Left(errorForm), request.userOpt, request.request)),
          mod => {
            userDataApi.update[MT, F](id, transformer(mod), logMsg = getLogMessage).map { citem =>
              UpdateRequest(request.item, Right(citem), request.userOpt, request)
            } recover {
              case ValidationError(errorSet) =>
                val filledForm = mod.getFormErrors(errorSet, form.fill(mod))
                UpdateRequest(request.item, Left(filledForm), request.userOpt, request)
            }
          }
        )
      }
    }
}
