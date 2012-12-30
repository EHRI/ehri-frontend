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

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Agent and
 * DocumentaryUnit itself.
 */
trait DocumentaryUnitCreator[F <: Persistable, T <: AccessibleEntity] extends EntityController[T] {

  import play.api.mvc.Call
  import play.api.mvc.RequestHeader
  type DocFormViewType = (T, Form[DocumentaryUnitF], Call, UserProfile, RequestHeader) => play.api.templates.Html
  val docFormView: DocFormViewType
  val docForm: Form[DocumentaryUnitF]
  val docShowAction: String => Call
  val docCreateAction: String => Call

  def docCreate(id: String) = withItemPermission(id, PermissionType.Create, ContentType.DocumentaryUnit) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map { item => Ok(docFormView(builder(item), docForm, docCreateAction(id), user, request)) }
        }
      }
  }

  def docCreatePost(id: String) = withItemPermission(id, PermissionType.Create, ContentType.DocumentaryUnit) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)

      def renderForm(form: Form[models.forms.DocumentaryUnitF]): AsyncResult = AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map { item =>
            BadRequest(docFormView(builder(item), form, docCreateAction(id), user, request))
          }
        }
      }

      docForm.bindFromRequest.fold(
        errorForm => renderForm(errorForm),
        doc => {
          AsyncRest {
            rest.EntityDAO(entityType, maybeUser)
              .createInContext(EntityType.DocumentaryUnit, id, doc.toData).map { itemOrErr =>
                // If we have an error, check if it's a validation error.
                // If so, we need to merge those errors back into the form
                // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case err: rest.ValidationError => {
                      val serverErrors = doc.errorsToForm(err.errorSet)
                      val filledForm = docForm.fill(doc).copy(errors = docForm.errors ++ serverErrors)
                      Right(renderForm(filledForm))
                    }
                    case e => Left(e)
                  }
                } else itemOrErr.right.map { item => Redirect(docShowAction(item.id)) }
              }
          }
        }
      )
  }
}
