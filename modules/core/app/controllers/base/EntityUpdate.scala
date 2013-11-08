package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.Form
import defines.PermissionType
import models.UserProfile
import play.api.Logger
import models.json.{RestReadable, RestConvertable}
import play.api.data.FormError
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future

/**
 * Controller trait which updates an AccessibleEntity.
 *
 * @tparam F the Entity's formable representation
 * @tparam MT the Entity's meta representation
 */
trait EntityUpdate[F <: Model with Persistable, MT <: MetaModel[F]] extends EntityRead[MT] {

  type UpdateCallback = MT => Either[Form[F], MT] => Option[UserProfile] => Request[AnyContent] => SimpleResult
  type AsyncUpdateCallback = MT => Either[Form[F], MT] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]

  def updateAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
    implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  /**
   * Loads the item with the given id and checks update permissions
   * exist. Then updates using the bound values of the given form.
   */
  object updatePostAction {
    def async(id: String, form: Form[F], transform: F => F = identity)(f: AsyncUpdateCallback)(
        implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Update, contentType) {
          item => implicit userOpt => implicit request =>
        updateAction.async(item, form, transform)(f)
      }
    }

    def apply(id: String, form: Form[F], transform: F => F = identity)(f: UpdateCallback)(
      implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
      async(id, form, transform)(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }

  /**
   * Updates an item, binding the form to the request, optionally
   * transforming it prior to being saved. Since the item itself is
   * given it is assumed that update perms exist (and the server will
   * error if they don't)
   *
   * @param item
   * @param form
   * @param transform
   * @return
   */
  object updateAction {
    def async(item: MT, form: Form[F], transform: F => F = identity)(f: AsyncUpdateCallback)(
        implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: RestConvertable[F], rd: RestReadable[MT]) = {
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
            case err: rest.ValidationError => {
              val serverErrors: Seq[FormError] = doc.errorsToForm(err.errorSet)
              val filledForm = form.fill(doc).copy(errors = form.errors ++ serverErrors)
              f(item)(Left(filledForm))(userOpt)(request)
            }
          }
        }
      )
    }

    def apply(item: MT, form: Form[F], transform: F => F = identity)(f: UpdateCallback)(
      implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: RestConvertable[F], rd: RestReadable[MT]) = {
      async(item, form, transform)(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }
}
