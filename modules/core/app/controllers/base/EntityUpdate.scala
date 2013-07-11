package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.Form
import defines.PermissionType
import models.UserProfileMeta
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

  def updateAction(id: String)(f: MT => Option[UserProfileMeta] => Request[AnyContent] => Result)(
    implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def updatePostAction(id: String, form: Form[F])(f: MT => Either[Form[F],MT] => Option[UserProfileMeta] => Request[AnyContent] => Result)(
      implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>

      form.bindFromRequest.fold(
        errorForm => {
          Logger.logger.debug("Form errors: {}", errorForm.errors)
          f(item)(Left(errorForm))(userOpt)(request)
        },
        success = doc => {
          AsyncRest {
            rest.EntityDAO(entityType, userOpt).update(id, doc, logMsg = getLogMessage).map { itemOrErr =>
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
}
