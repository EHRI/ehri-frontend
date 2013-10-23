package controllers.base

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

/**
 * Controller trait for creating AccessibleEntities.
 */
trait EntityCreate[F <: Model with Persistable, MT <: MetaModel[F]] extends EntityRead[MT] {

  type CreateCallback = Either[(Form[F],Form[List[String]]),MT] => Option[UserProfile] => Request[AnyContent] => SimpleResult
  type AsyncCreateCallback = Either[(Form[F],Form[List[String]]),MT] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult]

  /**
   * Create an item. Because the item must have an initial visibility we need
   * to collect the users and group lists at the point of creation
   *
   * @param f
   * @return
   */
  def createAction(f: Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => SimpleResult) = {
    withContentPermission.async(PermissionType.Create, contentType) { implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(users)(groups)(userOpt)(request)
      }
    }
  }

  object createPostAction {
    def async(form: Form[F])(f: AsyncCreateCallback)(implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
      withContentPermission.async(PermissionType.Create, contentType) { implicit userOpt => implicit request =>
        form.bindFromRequest.fold(
          errorForm => f(Left((errorForm,VisibilityForm.form)))(userOpt)(request),
          doc => {
            AsyncRest.async {
              val accessors = VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
              rest.EntityDAO(entityType, userOpt)
                  .create(doc, accessors, logMsg = getLogMessage).map { itemOrErr =>
                // If we have an error, check if it's a validation error.
                // If so, we need to merge those errors back into the form
                // and redisplay it...
                if (itemOrErr.isLeft) {
                  itemOrErr.left.get match {
                    case rest.ValidationError(errorSet) => {
                      val serverErrors: Seq[FormError] = doc.errorsToForm(errorSet)
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

    def apply(form: Form[F])(f: CreateCallback)(implicit fmt: RestConvertable[F], rd: RestReadable[MT]) = {
      async(form)(f.andThen(_.andThen(_.andThen(t => immediate(t)))))
    }
  }
}

