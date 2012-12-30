package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base.AccessibleEntity
import play.api.mvc.RequestHeader
import play.api.mvc.Call
import models.base.Persistable
import play.api.data.{ Form, FormError }
import defines.PermissionType
import models.UserProfile

/**
 * Controller trait for creating AccessibleEntities.
 *
 * @tparam F the Entity's formable representation
 * @tparam T the Entity's built representation
 */
trait EntityCreate[F <: Persistable, T <: AccessibleEntity] extends EntityRead[T] {
  type FormViewType = (Option[T], Form[F], Call, UserProfile, RequestHeader) => play.api.templates.Html
  val createAction: Call
  val formView: FormViewType
  val form: Form[F]

  def create = withContentPermission(PermissionType.Create, contentType) { implicit user =>
    implicit request =>
      Ok(formView(None, form, createAction, user, request))
  }

  def createPost = withContentPermission(PermissionType.Create, contentType) { implicit user =>
    implicit request =>
      form.bindFromRequest.fold(
        errorForm => BadRequest(formView(None, errorForm, createAction, user, request)),
        doc => {
          implicit val maybeUser = Some(user)
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser)
              .create(doc.toData).map { itemOrErr =>
                // If we have an error, check if it's a validation error.
                // If so, we need to merge those errors back into the form
                // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case err: rest.ValidationError => {
                      val serverErrors: Seq[FormError] = doc.errorsToForm(err.errorSet)
                      val filledForm = form.fill(doc).copy(errors = form.errors ++ serverErrors)
                      Right(BadRequest(formView(None, filledForm, createAction, user, request)))
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

