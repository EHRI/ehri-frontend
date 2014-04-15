package controllers.generic

import play.api.mvc._
import play.api.data.Form
import play.api.libs.concurrent.Execution.Implicits._
import defines.{ContentTypes, PermissionType}
import models.base._
import models.UserProfile
import forms.VisibilityForm
import models.json.{RestReadable, RestConvertable}
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future
import backend.rest.ValidationError

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Repository and
 * DocumentaryUnit itself.
 */
trait Creator[CF <: Model with Persistable, CMT <: MetaModel[CF], MT <: MetaModel[_]] extends Read[MT] {

  /**
   * Callback signature.
   */
  type CreationContextCallback = MT => Either[(Form[CF],Form[List[String]]),CMT] => Option[UserProfile] => Request[AnyContent] => Result
  type AsyncCreationContextCallback = MT => Either[(Form[CF],Form[List[String]]),CMT] => Option[UserProfile] => Request[AnyContent] => Future[Result]

  object childCreateAction {
    def async(id: String, ct: ContentTypes.Value)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: RestReadable[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Create, contentType, Some(ct)) { item => implicit userOpt => implicit request =>
        getUsersAndGroups.async { users => groups =>
          f(item)(users)(groups)(userOpt)(request)
        }
      }
    }
    def apply(id: String, ct: ContentTypes.Value)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
      async(id, ct)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t)))))))
    }
  }

  object childCreatePostAction {
    def async(id: String, form: Form[CF], ct: ContentTypes.Value)(f: AsyncCreationContextCallback)(
                implicit fmt: RestConvertable[CF], crd: RestReadable[CMT], rd: RestReadable[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Create, contentType, Some(ct)) { item => implicit userOpt => implicit request =>
        createChildPostAction(item, form, ct)(f)
      }
    }

    def apply(id: String, form: Form[CF], ct: ContentTypes.Value)(f: CreationContextCallback)(
      implicit fmt: RestConvertable[CF], crd: RestReadable[CMT], rd: RestReadable[MT]) = {
      async(id, form, ct)(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }

  def createChildPostAction(item: MT, form: Form[CF], ct: ContentTypes.Value)(f: AsyncCreationContextCallback)(
      implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: RestConvertable[CF], crd: RestReadable[CMT], rd: RestReadable[MT]): Future[Result] = {
    form.bindFromRequest.fold(
      errorForm => f(item)(Left((errorForm, VisibilityForm.form)))(userOpt)(request),
      citem => {
        val accessors = VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
        backend.createInContext[MT, CF, CMT](item.id, ct, citem, accessors, logMsg = getLogMessage).flatMap { citem =>
          f(item)(Right(citem))(userOpt)(request)
        } recoverWith {
          case ValidationError(errorSet) => {
            val filledForm = citem.getFormErrors(errorSet, form.fill(citem))
            f(item)(Left((filledForm, VisibilityForm.form)))(userOpt)(request)
          }
        }
      }
    )
  }
}
