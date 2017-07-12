package controllers.generic

import play.api.mvc._
import play.api.data.Form
import defines.PermissionType
import models.base._
import models.UserProfile
import forms.VisibilityForm
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.Future
import backend.rest.{DataHelpers, ValidationError}
import backend.{Readable, Writable, ContentType}

/**
 * Controller trait for extending Entity classes which server as
 * context for the creation of DocumentaryUnits, i.e. Repository and
 * DocumentaryUnit itself.
 */
trait Creator[CF <: Model with Persistable, CMT <: MetaModel[CF], MT <: MetaModel[_]] extends Write {

  this: Read[MT] =>

  protected def dataHelpers: DataHelpers

  case class NewChildRequest[A](
    item: MT,
    users: Seq[(String,String)],
    groups: Seq[(String,String)],
    userOpt: Option[UserProfile],
    request: Request[A]
    ) extends WrappedRequest[A](request)
  with WithOptionalUser

  private[generic] def NewChildTransformer(implicit ct: ContentType[MT]) = new CoreActionTransformer[ItemPermissionRequest, NewChildRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[NewChildRequest[A]] = {
      dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        NewChildRequest(request.item, users, groups, request.userOpt, request)
      }
    }
  }

  protected def NewChildAction(itemId: String)(implicit ct: ContentType[MT], cct: ContentType[CMT]): ActionBuilder[NewChildRequest, AnyContent] =
    WithParentPermissionAction(itemId, PermissionType.Create, cct.contentType) andThen NewChildTransformer

  case class CreateChildRequest[A](
     item: MT,
     formOrItem: Either[(Form[CF],Form[Seq[String]]),CMT],
     userOpt: Option[UserProfile],
     request: Request[A]
     ) extends WrappedRequest[A](request)
  with WithOptionalUser

  private[generic] def CreateChildTransformer(id: String, form: Form[CF], extraParams: ExtraParams = defaultExtra)(implicit ct: ContentType[MT], fmt: Writable[CF], crd: Readable[CMT], cct: ContentType[CMT]) =
    new CoreActionTransformer[ItemPermissionRequest, CreateChildRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[CreateChildRequest[A]] = {
        implicit val req = request
        val extra = extraParams.apply(request.request)
        val visForm = VisibilityForm.form.bindFromRequest
        form.bindFromRequest.fold(
          errorForm => immediate(CreateChildRequest(request.item, Left((errorForm, visForm)), request.userOpt, request.request)),
          citem => {
            val accessors = visForm.value.getOrElse(Nil)
            userDataApi.createInContext[MT, CF, CMT](id, citem, accessors, params = extra, logMsg = getLogMessage).map { citem =>
              CreateChildRequest(request.item, Right(citem), request.userOpt, request)
            } recover {
              case ValidationError(errorSet) =>
                val filledForm = citem.getFormErrors(errorSet, form.fill(citem))
                CreateChildRequest(request.item, Left((filledForm, visForm)), request.userOpt, request)
            }
          }
        )
      }
    }

  protected def CreateChildAction(id: String, form: Form[CF], extraParams: ExtraParams = defaultExtra)(
    implicit fmt: Writable[CF], crd: Readable[CMT], rd: Readable[MT], ct: ContentType[MT], cct: ContentType[CMT]): ActionBuilder[CreateChildRequest, AnyContent] =
    WithParentPermissionAction(id, PermissionType.Create, cct.contentType) andThen CreateChildTransformer(id, form, extraParams)
  
  
  /**
   * Functor to extract arbitrary DAO params from a request...
   */
  type ExtraParams = Request[_] => Map[String,Seq[String]]

  protected def defaultExtra: ExtraParams = (request: Request[_]) => Map.empty
}
