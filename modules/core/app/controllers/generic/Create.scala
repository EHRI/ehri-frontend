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
trait Create[F <: Model with Persistable, MT <: MetaModel[F]] extends Generic {

  this: Read[MT] =>

  /**
   * A request containing id->name tuples for available users
   * and groups.
   * @param users a seq of id -> name tuples for users on the system
   * @param groups a seq of id -> name tuples for users on the system
   * @param userOpt an optional profile
   * @param request the underlying request
   * @tparam A the type of the underlying request
   */
  case class UserGroupsRequest[A](
    users: Seq[(String,String)],
    groups: Seq[(String,String)],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class CreateRequest[A](
    formOrItem: Either[(Form[F],Form[List[String]]),MT],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def NewItemAction(implicit ct: BackendContentType[MT]) =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new ActionTransformer[OptionalUserRequest, UserGroupsRequest] {
      override protected def transform[A](request: OptionalUserRequest[A]): Future[UserGroupsRequest[A]] = {
        for {
          users <- RestHelpers.getUserList
          groups <- RestHelpers.getGroupList
        } yield UserGroupsRequest(users, groups, request.userOpt, request)
      }
    }

  protected def CreateItemAction(form: Form[F], pf: Request[_] => Map[String,Seq[String]] = _ => Map.empty)(implicit fmt: BackendWriteable[F], rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new ActionTransformer[OptionalUserRequest, CreateRequest] {
      def transform[A](request: OptionalUserRequest[A]): Future[CreateRequest[A]] = {
        implicit val req = request
        val visForm = VisibilityForm.form.bindFromRequest
        form.bindFromRequest.fold(
          errorForm => immediate(CreateRequest(Left((errorForm, visForm)), request.userOpt, request.request)),
          doc => {
            val accessors = visForm.value.getOrElse(Nil)
            backend.create(doc, accessors, params = pf(request), logMsg = getLogMessage).map { item =>
              CreateRequest(Right(item), request.userOpt, request)
            } recover {
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
              case ValidationError(errorSet) =>
                val filledForm = doc.getFormErrors(errorSet, form.fill(doc))
                CreateRequest(Left((filledForm, visForm)), request.userOpt, request)
            }
          }
        )
      }
    }
}

