package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import play.api.mvc._
import models.base.Persistable
import play.api.data.{ Form, FormError }
import models.base.Formable
import defines.PermissionType
import models.{UserProfile, Entity}

/**
 * Controller trait which updates an AccessibleEntity.
 *
 * @tparam F the Entity's formable representation
 * @tparam T the Entity's built representation
 */
trait EntityUpdate[F <: Persistable, T <: AccessibleEntity with Formable[F]] extends EntityRead[T] {
  val formView: EntityCreate[F, T]#FormViewType
  val form: Form[F]

  val showAction: String => Call

  def updateAction(id: String)(f: Entity => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { implicit user =>
      implicit request =>
        getEntity(id, Some(user)) { item =>
          f(item)(user)(request)
        }
    }
  }

  def updatePostAction(id: String, form: Form[F])(f: Either[Form[F],Entity] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)

        form.bindFromRequest.fold(
          errorForm => f(Left(errorForm))(user)(request),
          success = doc => {
            AsyncRest {
              rest.EntityDAO(entityType, maybeUser)
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
                        Right(f(Left(filledForm))(user)(request))
                      }
                      case e => Left(e)
                    },
                    item => Right(f(Right(item))(user)(request))
                  )
              }
            }
          }
        )
    }
  }
}
