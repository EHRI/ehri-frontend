package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call
import models.base.Persistable
import play.api.data.{Form,FormError}
import defines.PermissionType

trait EntityCreate[F <: Persistable, T <: AccessibleEntity] extends EntityRead[T] {
  type FormViewType = (Option[T], Form[F], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val createAction: Call
  val formView: FormViewType
  val form: Form[F]

  def create = userProfileAction { implicit maybeUser =>
    implicit request =>
    EnsurePermission(PermissionType.Create) {
      Ok(formView(None, form, createAction, maybeUser, request))      
    }
  }

  def createPost = userProfileAction { implicit maybeUser =>
    implicit request =>
      form.bindFromRequest.fold(
        errorForm => BadRequest(formView(None, errorForm, createAction, maybeUser, request)),
        doc => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser.flatMap(_.profile))
              .create(doc.toData).map { itemOrErr =>
                // If we have an error, check if it's a validation error.
                // If so, we need to merge those errors back into the form
                // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case err: rest.ValidationError => {
                      val serverErrors: Seq[FormError] = doc.errorsToForm(err.errorSet)
                      val filledForm = form.fill(doc).copy(errors=form.errors ++ serverErrors)
                      Right(BadRequest(formView(None, filledForm, createAction, maybeUser, request)))
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

