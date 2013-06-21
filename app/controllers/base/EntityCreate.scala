package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import play.api.mvc._
import models.base.Persistable
import play.api.data._
import defines.PermissionType
import models.{Entity, UserProfile}
import models.forms.VisibilityForm
import rest.EntityDAO
import play.api.libs.json.Writes
import models.json.RestConvertable

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
      getUsersAndGroups { users => groups =>
        f(users)(groups)(userOpt)(request)
      }
    }
  }

  def createPostAction(form: Form[F])(f: Either[(Form[F],Form[List[String]]),Entity] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit fmt: RestConvertable[F]) = {
    withContentPermission(PermissionType.Create, contentType) { implicit userOpt => implicit request =>
      form.bindFromRequest.fold(
        errorForm => f(Left((errorForm,VisibilityForm.form)))(userOpt)(request),
        doc => {
          AsyncRest {
            val accessors = VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
            rest.EntityDAO(entityType, userOpt)
                .create(doc, accessors, logMsg = getLogMessage).map { itemOrErr =>
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
              if (itemOrErr.isLeft) {
                itemOrErr.left.get match {
                  case err: rest.ValidationError => {
                    val serverErrors: Seq[FormError] = doc.errorsToForm(err.errorSet)
                    val filledForm = form.fill(doc).copy(errors = form.errors ++ serverErrors)
                    Right(f(Left((filledForm, VisibilityForm.form)))(userOpt)(request))
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

