package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call
import models.base.Persistable
import play.api.data.{Form,FormError}
import models.base.Formable

trait EntityUpdate[F <: Persistable, T <: AccessibleEntity with Formable[F]] extends EntityRead[T] {
  val updateAction: String => Call
  val formView: EntityCreate[F,T]#FormViewType
  val form: Form[F]

  def update(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            val doc: T = builder(item)
            Ok(formView(Some(doc), form.fill(doc.to), updateAction(id), maybeUser, request))
          }
        }
      }
  }

  def updatePost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      form.bindFromRequest.fold(
        errorForm => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser.flatMap(_.profile)).get(id).map { itemOrErr =>
              itemOrErr.right.map { item =>
                val doc: T = builder(item)
                BadRequest(formView(Some(doc), errorForm, updateAction(id), maybeUser, request))
              }
            }
          }
        },
        doc => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser.flatMap(_.profile))
              .update(id, doc.toData).map { itemOrErr =>
                // If we have an error, check if it's a validation error.
                // If so, we need to merge those errors back into the form
                // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case err: rest.ValidationError => {
                      
                      val serverErrors: Seq[FormError] = err.errorSet.errorsFor.flatMap { case(field, list) =>
                        list.map { errorItem =>
                          FormError(field, errorItem)
                        }
                      }.toSeq
                      val filledForm = form.fill(doc).copy(errors=serverErrors)
                      Right(BadRequest(formView(None, filledForm, updateAction(id), maybeUser, request)))
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
