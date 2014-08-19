package controllers.generic

import play.api.mvc._
import play.api.data.Form
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.base._
import models.UserProfile
import forms.VisibilityForm
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future
import backend.rest.ValidationError
import backend.{BackendWriteable, BackendContentType, BackendResource}

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Repository and
 * DocumentaryUnit itself.
 */
trait Creator[CF <: Model with Persistable, CMT <: MetaModel[CF], MT <: MetaModel[_]] extends Read[MT] {

  /**
   * Functor to extract arbitrary DAO params from a request...
   */
  type ExtraParams[A] = Request[A] => Map[String,Seq[String]]
  def defaultExtra[A]: ExtraParams[A] = (request: Request[A]) => Map.empty

  /**
   * Callback signature.
   */
  type CreationContextCallback = MT => Either[(Form[CF],Form[List[String]]),CMT] => Option[UserProfile] => Request[AnyContent] => Result
  type AsyncCreationContextCallback = MT => Either[(Form[CF],Form[List[String]]),CMT] => Option[UserProfile] => Request[AnyContent] => Future[Result]

  object childCreateAction {
    def async(id: String)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: _root_.backend.BackendReadable[MT], rs: BackendResource[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      withItemPermission.async[MT](id, PermissionType.Create, Some(cct.contentType)) { item => implicit userOpt => implicit request =>
        getUsersAndGroups.async { users => groups =>
          f(item)(users)(groups)(userOpt)(request)
        }
      }
    }
    def apply(id: String)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: _root_.backend.BackendReadable[MT], rs: BackendResource[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t)))))))
    }
  }

  object childCreatePostAction {
    def async(id: String, form: Form[CF], extraParams: ExtraParams[AnyContent] = defaultExtra)(f: AsyncCreationContextCallback)(
                implicit fmt: BackendWriteable[CF], crd: _root_.backend.BackendReadable[CMT], rd: _root_.backend.BackendReadable[MT], rs: BackendResource[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      withItemPermission.async[MT](id, PermissionType.Create, Some(cct.contentType)) { item => implicit userOpt => implicit request =>
        createChildPostAction(item, form, extraParams)(f)
      }
    }

    def apply(id: String, form: Form[CF], extraParams: ExtraParams[AnyContent] = defaultExtra)(f: CreationContextCallback)(
      implicit fmt: BackendWriteable[CF], crd: _root_.backend.BackendReadable[CMT], rd: _root_.backend.BackendReadable[MT], rs: BackendResource[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      async(id, form)(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }

  def createChildPostAction(item: MT, form: Form[CF], extraParams: ExtraParams[AnyContent])(f: AsyncCreationContextCallback)(
      implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: BackendWriteable[CF], crd: _root_.backend.BackendReadable[CMT], rd: _root_.backend.BackendReadable[MT], rs: BackendResource[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]): Future[Result] = {
    val extra = extraParams.apply(request)
    form.bindFromRequest.fold(
      errorForm => f(item)(Left((errorForm, VisibilityForm.form)))(userOpt)(request),
      citem => {
        val accessors = VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
        backend.createInContext[MT, CF, CMT](item.id, cct.contentType, citem, accessors, params = extra, logMsg = getLogMessage).flatMap { citem =>
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
