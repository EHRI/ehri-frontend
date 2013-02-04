package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import play.api.mvc._
import models.base.Persistable
import play.api.data.{ Form, FormError }
import defines.PermissionType
import models.{Entity, UserProfile}
import models.forms.VisibilityForm
import rest.EntityDAO

/**
 * Controller trait for creating AccessibleEntities.
 *
 * @tparam F the Entity's formable representation
 * @tparam T the Entity's built representation
 */
trait EntityCreate[F <: Persistable, T <: AccessibleEntity] extends EntityRead[T] {

  /**
   * Create an item. Because the item must have an initial visibility we need
   * to collect the users and group lists at the point of creation
   *
   * @param f
   * @return
   */
  def createAction(f: Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withContentPermission(PermissionType.Create, contentType) { implicit userOpt => implicit request =>
      getGroups { users => groups =>
        f(users)(groups)(userOpt)(request)
      }
    }
  }

  def createPostAction(form: Form[F])(f: Either[(Form[F],Form[List[String]]),Entity] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withContentPermission(PermissionType.Create, contentType) { implicit userOpt =>
      implicit request =>
      // FIXME: This is really gross, but it's necessary because
      // Play's forms do not yet support extracting multiselect
      // values properly withsome some unfortunately massaging.
        val accessorForm = VisibilityForm.form
          .bindFromRequest(fixMultiSelects(request.body.asFormUrlEncoded, rest.RestPageParams.ACCESSOR_PARAM))
        val accessors = accessorForm.value.getOrElse(List())
        form.bindFromRequest.fold(
          errorForm => f(Left((errorForm,accessorForm)))(userOpt)(request),
          doc => {
            AsyncRest {
            rest.EntityDAO(entityType, userOpt)
              .create(doc, accessors).map { itemOrErr =>
            // If we have an error, check if it's a validation error.
            // If so, we need to merge those errors back into the form
            // and redisplay it...
              if (itemOrErr.isLeft) {
                itemOrErr.left.get match {
                  case err: rest.ValidationError => {
                    val serverErrors: Seq[FormError] = doc.errorsToForm(err.errorSet)
                    val filledForm = form.fill(doc).copy(errors = form.errors ++ serverErrors)
                    Right(f(Left((filledForm,accessorForm)))(userOpt)(request))
                  }
                  case e => Left(e)
                }
              } else itemOrErr.right.map {
                item => f(Right(item))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }
}

