package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import play.api.mvc._
import models.base.Persistable
import play.api.data.{ Form, FormError }
import models.base.Formable
import defines.PermissionType
import models.{UserProfile, Entity}
import play.api.Logger

/**
 * Controller trait which updates an AccessibleEntity.
 *
 * @tparam F the Entity's formable representation
 * @tparam T the Entity's built representation
 */
trait EntityUpdate[F <: Persistable, T <: AccessibleEntity with Formable[F]] extends EntityRead[T] {

  def updateAction(id: String)(f: Entity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def updatePostAction(id: String, form: Form[F])(f: Entity => Either[Form[F],Entity] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>

      form.bindFromRequest.fold(
        errorForm => {
          Logger.logger.debug("Form errors: {}", errorForm.errors)
          f(item)(Left(errorForm))(userOpt)(request)
        },
        success = doc => {
          AsyncRest {
            rest.EntityDAO(entityType, userOpt)
              .update(id, doc).map {
              itemOrErr =>
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
