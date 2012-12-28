package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.Call
import models.base.Persistable
import play.api.data.{ Form, FormError }
import models.base.Formable
import defines.PermissionType

trait EntityUpdate[F <: Persistable, T <: AccessibleEntity with Formable[F]] extends EntityRead[T] {
  val updateAction: String => Call
  val formView: EntityCreate[F, T]#FormViewType
  val form: Form[F]

  def update(id: String) = withItemPermission(id, PermissionType.Update) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: T = builder(item)
            Ok(formView(Some(doc), form.fill(doc.to), updateAction(id), user, request))
          }
        }
      }
  }

  def updatePost(id: String) = withItemPermission(id, PermissionType.Update) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      form.bindFromRequest.fold(
        errorForm => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
              itemOrErr.right.map { item =>
                val doc: T = builder(item)
                BadRequest(formView(Some(doc), errorForm, updateAction(id), user, request))
              }
            }
          }
        },
        doc => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser)
              .update(id, doc.toData).map { itemOrErr =>
                // If we have an error, check if it's a validation error.
                // If so, we need to merge those errors back into the form
                // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case err: rest.ValidationError => {
                      val serverErrors: Seq[FormError] = doc.errorsToForm(err.errorSet)
                      val filledForm = form.fill(doc).copy(errors = form.errors ++ serverErrors)
                      Right(BadRequest(formView(None, filledForm, updateAction(id), user, request)))
                    }
                    case e => Left(e)
                  }
                } else itemOrErr.right.map { item => Redirect(showAction(item.id)) }
              }
          }
        }
      )
  }
}
