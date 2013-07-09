package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.{Model, MetaModel, Persistable}
import play.api.mvc._
import play.api.data._
import defines.PermissionType
import models.UserProfileMeta
import forms.VisibilityForm
import models.json.{RestReadable, RestConvertable}

/**
 * Controller trait for creating AccessibleEntities.
 *
 * @tparam F the Entity's formable representation
 * @tparam MT the Entity's meta representation
 */
trait EntityCreate[F <: Model with Persistable, MT <: MetaModel[F]] extends EntityRead[MT] {

  /**
   * Create an item. Because the item must have an initial visibility we need
   * to collect the users and group lists at the point of creation
   *
   * @param f
   * @return
   */
  def createAction(f: Seq[(String,String)] => Seq[(String,String)] => Option[UserProfileMeta] => Request[AnyContent] => Result) = {
    withContentPermission(PermissionType.Create, contentType) { implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(users)(groups)(userOpt)(request)
      }
    }
  }

  def createPostAction(form: Form[F])(f: Either[(Form[F],Form[List[String]]),MT] => Option[UserProfileMeta] => Request[AnyContent] => Result)(
      implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
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

