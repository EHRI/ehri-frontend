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
import backend.rest.{RestHelpers, ValidationError}
import backend.{BackendReadable, BackendWriteable, BackendContentType}

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Repository and
 * DocumentaryUnit itself.
 */
trait Creator[CF <: Model with Persistable, CMT <: MetaModel[CF], MT <: MetaModel[_]] extends Read[MT] {

  case class NewChildRequest[A](
    item: MT,
    users: Seq[(String,String)],
    groups: Seq[(String,String)],
    profileOpt: Option[UserProfile],
    request: Request[A]
    ) extends WrappedRequest[A](request)
  with WithOptionalProfile

  private[generic] def NewChildTransformer(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = new ActionTransformer[ItemPermissionRequest, NewChildRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[NewChildRequest[A]] = {
      for {
        users <- RestHelpers.getUserList
        groups <- RestHelpers.getGroupList
      } yield NewChildRequest(request.item, users, groups, request.profileOpt, request)
    }
  }

  protected def NewChildAction(itemId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) =
    WithParentPermissionAction(itemId, PermissionType.Create, cct.contentType) andThen NewChildTransformer

  case class CreateChildRequest[A](
     item: MT,
     formOrItem: Either[(Form[CF],Form[List[String]]),CMT],
     profileOpt: Option[UserProfile],
     request: Request[A]
     ) extends WrappedRequest[A](request)
  with WithOptionalProfile

  private[generic] def CreateChildTransformer(id: String, form: Form[CF], extraParams: ExtraParams = defaultExtra)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], fmt: BackendWriteable[CF], crd: BackendReadable[CMT], cct: BackendContentType[CMT]) =
    new ActionTransformer[ItemPermissionRequest, CreateChildRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[CreateChildRequest[A]] = {
        implicit val req = request
        val extra = extraParams.apply(request.request)
        form.bindFromRequest.fold(
          errorForm => immediate(CreateChildRequest(request.item, Left((errorForm,VisibilityForm.form)), request.profileOpt, request.request)),
          citem => {
            val accessors = VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
            backend.createInContext[MT, CF, CMT](id, cct.contentType, citem, accessors, params = extra, logMsg = getLogMessage).map { citem =>
              CreateChildRequest(request.item, Right(citem), request.profileOpt, request)
            } recover {
              case ValidationError(errorSet) =>
                val filledForm = citem.getFormErrors(errorSet, form.fill(citem))
                CreateChildRequest(request.item, Left((filledForm, VisibilityForm.form)), request.profileOpt, request)
            }
          }
        )
      }
    }


  protected def CreateChildAction(id: String, form: Form[CF], extraParams: ExtraParams = defaultExtra)(implicit fmt: BackendWriteable[CF], crd: BackendReadable[CMT], rd: BackendReadable[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) =
    WithParentPermissionAction(id, PermissionType.Create, cct.contentType) andThen CreateChildTransformer(id, form, extraParams)
  
  
  /**
   * Functor to extract arbitrary DAO params from a request...
   */
  type ExtraParams = Request[_] => Map[String,Seq[String]]
  def defaultExtra: ExtraParams = (request: Request[_]) => Map.empty

  /**
   * Callback signature.
   */
  type CreationContextCallback = MT => Either[(Form[CF],Form[List[String]]),CMT] => Option[UserProfile] => Request[AnyContent] => Result
  type AsyncCreationContextCallback = MT => Either[(Form[CF],Form[List[String]]),CMT] => Option[UserProfile] => Request[AnyContent] => Future[Result]

  @deprecated(message = "Use NewChildAction instead", since = "1.0.2")
  object childCreateAction {
    def async(id: String)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      withItemPermission.async[MT](id, PermissionType.Create, Some(cct.contentType)) { item => implicit userOpt => implicit request =>
        getUsersAndGroups.async { users => groups =>
          f(item)(users)(groups)(userOpt)(request)
        }
      }
    }
    def apply(id: String)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t)))))))
    }
  }

  @deprecated(message = "Use CreateChildAction instead", since = "1.0.2")
  object childCreatePostAction {
    def async(id: String, form: Form[CF], extraParams: ExtraParams = defaultExtra)(f: AsyncCreationContextCallback)(
                implicit fmt: BackendWriteable[CF], crd: BackendReadable[CMT], rd: BackendReadable[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      withItemPermission.async[MT](id, PermissionType.Create, Some(cct.contentType)) { item => implicit userOpt => implicit request =>
        createChildPostAction(item, form, extraParams)(f)
      }
    }

    def apply(id: String, form: Form[CF], extraParams: ExtraParams = defaultExtra)(f: CreationContextCallback)(
      implicit fmt: BackendWriteable[CF], crd: BackendReadable[CMT], rd: BackendReadable[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]) = {
      async(id, form)(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }

  private def createChildPostAction(item: MT, form: Form[CF], extraParams: ExtraParams)(f: AsyncCreationContextCallback)(
      implicit userOpt: Option[UserProfile], request: Request[AnyContent], fmt: BackendWriteable[CF], crd: BackendReadable[CMT], rd: BackendReadable[MT], ct: BackendContentType[MT], cct: BackendContentType[CMT]): Future[Result] = {
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
