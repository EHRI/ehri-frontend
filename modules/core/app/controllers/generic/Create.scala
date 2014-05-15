package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import models.base.{Model, MetaModel, Persistable}
import play.api.mvc._
import play.api.data._
import defines.PermissionType
import models.UserProfile
import forms.VisibilityForm
import models.json.{RestReadable, RestConvertable}
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future
import backend.rest.ValidationError

/**
 * Controller trait for creating AccessibleEntities.
 */
trait Create[F <: Model with Persistable, MT <: MetaModel[F]] extends Generic[MT] {

  type CreateCallback = Either[(Form[F],Form[List[String]]),MT] => Option[UserProfile] => Request[AnyContent] => SimpleResult
  type AsyncCreateCallback = Either[(Form[F],Form[List[String]]),MT] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]

  /**
   * Create an item. Because the item must have an initial visibility we need
   * to collect the users and group lists at the point of creation
   */
  def createAction(f: Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => SimpleResult) = {
    withContentPermission.async(PermissionType.Create, contentType) { implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(users)(groups)(userOpt)(request)
      }
    }
  }

  object createPostAction {
    def async(form: Form[F], pf: Request[AnyContent] => Map[String,Seq[String]] = _ => Map.empty)(f: AsyncCreateCallback)(implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
      withContentPermission.async(PermissionType.Create, contentType) { implicit userOpt => implicit request =>
        form.bindFromRequest.fold(
          errorForm => f(Left((errorForm,VisibilityForm.form)))(userOpt)(request),
          doc => {
            val accessors = VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
            backend.create(doc, accessors, params = pf(request), logMsg = getLogMessage).flatMap { item =>
              f(Right(item))(userOpt)(request)
            } recoverWith {
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
              case ValidationError(errorSet) => {
                val filledForm = doc.getFormErrors(errorSet, form.fill(doc))
                f(Left((filledForm, VisibilityForm.form)))(userOpt)(request)
              }
            }
          }
        )
      }
    }

    def apply(form: Form[F])(f: CreateCallback)(implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
      async(form)(f.andThen(_.andThen(_.andThen(t => immediate(t)))))
    }
  }
}

