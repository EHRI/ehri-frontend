package controllers.generic

import backend.rest.{RestHelpers, ValidationError}
import backend.{BackendContentType, BackendReadable, BackendWriteable}
import defines.PermissionType
import forms.VisibilityForm
import models.UserProfile
import models.base.{MetaModel, Model, Persistable}
import play.api.data._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
 * Controller trait for creating AccessibleEntities.
 */
trait Create[F <: Model with Persistable, MT <: MetaModel[F]] extends Generic[MT] {

  self: Read[MT] =>

  case class NewRequest[A](
    users: Seq[(String,String)],
    groups: Seq[(String,String)],
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalProfile

  protected def NewItemTransformer(implicit ct: BackendContentType[MT]) = new ActionTransformer[OptionalProfileRequest, NewRequest] {
    override protected def transform[A](request: OptionalProfileRequest[A]): Future[NewRequest[A]] = {
      for {
        users <- RestHelpers.getUserList
        groups <- RestHelpers.getGroupList
      } yield NewRequest(users, groups, request.profileOpt, request)
    }
  }

  def NewItemAction(implicit ct: BackendContentType[MT]) =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen NewItemTransformer

  case class CreateRequest[A](
    formOrItem: Either[(Form[F],Form[List[String]]),MT],
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalProfile

  protected def CreatePostTransformer(form: Form[F], pf: Request[_] => Map[String,Seq[String]] = _ => Map.empty)(implicit fmt: BackendWriteable[F], rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    new ActionTransformer[OptionalProfileRequest, CreateRequest] {
      def transform[A](request: OptionalProfileRequest[A]): Future[CreateRequest[A]] = {
        implicit val req = request
        form.bindFromRequest.fold(
          errorForm => immediate(CreateRequest(Left((errorForm,VisibilityForm.form)), request.profileOpt, request.request)),
          doc => {
            val accessors = VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
            backend.create(doc, accessors, params = pf(request), logMsg = getLogMessage).map { item =>
              CreateRequest(Right(item), request.profileOpt, request)
            } recover {
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
              case ValidationError(errorSet) =>
                val filledForm = doc.getFormErrors(errorSet, form.fill(doc))
                CreateRequest(Left((filledForm, VisibilityForm.form)), request.profileOpt, request)
            }
          }
        )
      }
    }

  
  def CreateItemAction(form: Form[F], pf: Request[_] => Map[String,Seq[String]] = _ => Map.empty)(implicit fmt: BackendWriteable[F], rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen CreatePostTransformer(form, pf)


  type CreateCallback = Either[(Form[F],Form[List[String]]),MT] => Option[UserProfile] => Request[AnyContent] => Result
  type AsyncCreateCallback = Either[(Form[F],Form[List[String]]),MT] => Option[UserProfile] => Request[AnyContent] => Future[Result]

  /**
   * Create an item. Because the item must have an initial visibility we need
   * to collect the users and group lists at the point of creation
   */
  @deprecated(message = "Use NewItemAction instead", since = "1.0.2")
  object createAction {
    def async(f: Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit ct: BackendContentType[MT]) = {
      withContentPermission.async(PermissionType.Create, ct.contentType) { implicit userOpt => implicit request =>
        getUsersAndGroups.async { users => groups =>
          f(users)(groups)(userOpt)(request)
        }
      }
    }

    def apply(f: Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(implicit ct: BackendContentType[MT]) = {
      async(f.andThen(_.andThen(_.andThen(_.andThen(t => immediate(t))))))
    }
  }

  @deprecated(message = "Use CreatePostAction instead", since = "1.0.2")
  object createPostAction {
    def async(form: Form[F], pf: Request[AnyContent] => Map[String,Seq[String]] = _ => Map.empty)(f: AsyncCreateCallback)(implicit fmt: BackendWriteable[F], rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      withContentPermission.async(PermissionType.Create, ct.contentType) { implicit userOpt => implicit request =>
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

    def apply(form: Form[F])(f: CreateCallback)(implicit fmt: BackendWriteable[F], rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(form)(f.andThen(_.andThen(_.andThen(t => immediate(t)))))
    }
  }
}

