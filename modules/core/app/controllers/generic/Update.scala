package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.Form
import defines.PermissionType
import models.UserProfile
import play.api.Logger
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future
import backend.rest.ValidationError
import backend.{BackendReadable, BackendWriteable, BackendContentType, BackendResource}

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

  def EditAction(itemId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(itemId, PermissionType.Update)

  def UpdateAction(id: String, form: Form[F], transformer: F => F = identity)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], wd: BackendWriteable[F]) =
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

  type UpdateCallback = MT => Either[Form[F], MT] => Option[UserProfile] => Request[AnyContent] => Result
  type AsyncUpdateCallback = MT => Either[Form[F], MT] => Option[UserProfile] => Request[AnyContent] => Future[Result]

  @deprecated(message = "Use EditAction instead", since = "1.0.2")
  def updateAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
    implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Update) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  /**
   * Loads the item with the given id and checks update permissions
   * exist. Then updates using the bound values of the given form.
   */
  @deprecated(message = "Use UpdateAction instead", since = "1.0.2")
  object updatePostAction {
    def async(id: String, form: Form[F], transform: F => F = identity)(f: AsyncUpdateCallback)(
        implicit fmt: BackendWriteable[F], rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Update) {
          item => implicit userOpt => implicit request =>
        updateAction.async(item, form, transform)(f)
      }
    }

    def apply(id: String, form: Form[F], transform: F => F = identity)(f: UpdateCallback)(
      implicit fmt: BackendWriteable[F], rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(id, form, transform)(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }

  /**
   * Updates an item, binding the form to the request, optionally
   * transforming it prior to being saved. Since the item itself is
   * given it is assumed that update perms exist (and the server will
   * error if they don't)
   */
  private object updateAction {
    def async(item: MT, form: Form[F], transform: F => F = identity)(f: AsyncUpdateCallback)(
        implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: BackendWriteable[F], rd: BackendReadable[MT], rs: BackendResource[MT]) = {
      form.bindFromRequest.fold(
        errorForm => {
          Logger.logger.debug("Form errors: {}", errorForm.errors)
          f(item)(Left(errorForm))(userOpt)(request)
        },
        doc => {
          backend.update(item.id, transform(doc), logMsg = getLogMessage).flatMap { updated =>
            f(item)(Right(updated))(userOpt)(request)
          } recoverWith {
            // If we have an error, check if it's a validation error.
            // If so, we need to merge those errors back into the form
            // and redisplay it...
            case ValidationError(errorSet) => {
              val filledForm = doc.getFormErrors(errorSet, form.fill(doc))
              f(item)(Left(filledForm))(userOpt)(request)
            }
          }
        }
      )
    }

    def apply(item: MT, form: Form[F], transform: F => F = identity)(f: UpdateCallback)(
      implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: BackendWriteable[F], rd: BackendReadable[MT], rs: BackendResource[MT]) = {
      async(item, form, transform)(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }
}
