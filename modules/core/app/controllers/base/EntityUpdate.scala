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

/**
 * Controller trait which updates an AccessibleEntity.
 *
 * @tparam F the Entity's formable representation
 * @tparam MT the Entity's meta representation
 */
trait EntityUpdate[F <: Model with Persistable, MT <: MetaModel[F]] extends EntityRead[MT] {

  type UpdateCallback = MT => Either[Form[F], MT] => Option[UserProfile] => Request[AnyContent] => Result

  def updateAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(
    implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  /**
   * Loads the item with the given id and checks update permissions
   * exist. Then updates using the bound values of the given form.
   *
   * @param id        The item's id
   * @param form      The form yielding an item when bound
   * @param transform A function that can be used to transform the item
   *                  prior to being saved in the database (i.e. to reset
   *                  some values.)
   * @param f         A callback to run with the result of the action
   * @param fmt
   * @param rd
   * @return
   */
  def updatePostAction(id: String, form: Form[F], transform: F => F = identity)(f: UpdateCallback)(
      implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Update, contentType) {
        item => implicit userOpt => implicit request =>
      updateAction(item, form, transform)(f)
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
  def updateAction(item: MT, form: Form[F], transform: F => F = identity)(f: UpdateCallback)(
      implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: RestConvertable[F], rd: RestReadable[MT]) = {
    form.bindFromRequest.fold(
      errorForm => {
        Logger.logger.debug("Form errors: {}", errorForm.errors)
        f(item)(Left(errorForm))(userOpt)(request)
      },
      success = doc => {
        AsyncRest {
          rest.EntityDAO(entityType)
              .update(item.id, transform(doc), logMsg = getLogMessage).map { itemOrErr =>
            // If we have an error, check if it's a validation error.
            // If so, we need to merge those errors back into the form
            // and redisplay it...
            itemOrErr.fold(
              err => err match {
                case err: rest.ValidationError => {
                  val serverErrors: Seq[FormError] = doc.errorsToForm(err.errorSet)
                  val filledForm = form.fill(doc).copy(errors = form.errors ++ serverErrors)
                  Right(f(item)(Left(filledForm))(userOpt)(request))
                }
                case e => Left(e)
              },
              item => Right(f(item)(Right(item))(userOpt)(request))
            )
          }
        }
      }
    )
  }
}
