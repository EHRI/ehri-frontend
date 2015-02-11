package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.Form
import defines.PermissionType
import models.UserProfile
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future
import backend.rest.ValidationError
import backend.{BackendWriteable, BackendContentType}

/**
 * Controller trait which updates an AccessibleEntity.
 */
trait Update[F <: Model with Persistable, MT <: MetaModel[F]] extends Generic[MT] {

  this: Read[MT] =>

  case class UpdateRequest[A](
    item: MT,
    formOrItem: Either[Form[F], MT],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  def EditAction(itemId: String)(implicit ct: BackendContentType[MT]) =
    WithItemPermissionAction(itemId, PermissionType.Update)

  def UpdateAction(id: String, form: Form[F], transformer: F => F = identity)(implicit ct: BackendContentType[MT], wd: BackendWriteable[F]) =
    EditAction(id) andThen new ActionTransformer[ItemPermissionRequest, UpdateRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[UpdateRequest[A]] = {
        implicit val req = request
        form.bindFromRequest.fold(
          errorForm => immediate(UpdateRequest(request.item, Left(errorForm), request.userOpt, request.request)),
          mod => {
            backend.update[MT,F](id, transformer(mod), logMsg = getLogMessage).map { citem =>
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
