package controllers.base

import defines.{EntityType,ContentType}
import models.forms.DocumentaryUnitF
import models.base.Persistable
import models.base.AccessibleEntity
import play.api.data.Form
import play.api.libs.concurrent.execution.defaultContext
import defines.PermissionType
import models.UserProfile
import play.api.mvc.AsyncResult
import play.api.i18n.Messages

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Agent and
 * DocumentaryUnit itself.
 */
trait CreationContext[CF <: Persistable, T <: AccessibleEntity] extends EntityController[T] {

  import play.api.mvc.Call
  import play.api.mvc.RequestHeader

  val childContentType: ContentType.Value
  val childEntityType: EntityType.Value

  type ChildFormViewType = (T, Form[CF], Call, UserProfile, RequestHeader) => play.api.templates.Html
  val childFormView: ChildFormViewType
  val childForm: Form[CF]
  val childShowAction: String => Call
  val childCreateAction: String => Call

  def childCreate(id: String) = withItemPermission(id, PermissionType.Create, childContentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(childFormView(builder(item), childForm, childCreateAction(id), user, request)) }
        }
      }
  }

  def childCreatePost(id: String) = withItemPermission(id, PermissionType.Create, childContentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)

      def renderForm(form: Form[CF]): AsyncResult = AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            BadRequest(childFormView(builder(item), form, childCreateAction(id), user, request))
          }
        }
      }

      childForm.bindFromRequest.fold(
        errorForm => renderForm(errorForm),
        doc => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser)
              .createInContext(childEntityType, id, doc.toData).map { itemOrErr =>
                // If we have an error, check if it's a validation error.
                // If so, we need to merge those errors back into the form
                // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case err: rest.ValidationError => {
                      val serverErrors = doc.errorsToForm(err.errorSet)
                      val filledForm = childForm.fill(doc).copy(errors = childForm.errors ++ serverErrors)
                      Right(renderForm(filledForm))
                    }
                    case e => Left(e)
                  }
                } else itemOrErr.right.map {
                  item => Redirect(childShowAction(item.id)).flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
                }
              }
          }
        }
      )
  }
}
