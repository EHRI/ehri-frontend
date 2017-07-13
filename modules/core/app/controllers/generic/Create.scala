package controllers.generic

import services.rest.{DataHelpers, ValidationError}
import services.{ContentType, Readable, Writable}
import defines.PermissionType
import forms.VisibilityForm
import models.UserProfile
import models.base.{MetaModel, Model, Persistable}
import play.api.data._
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
 * Controller trait for creating AccessibleEntities.
 */
trait Create[F <: Model with Persistable, MT <: MetaModel[F]] extends Write {

  this: Read[MT] =>

  protected def dataHelpers: DataHelpers

  /**
   * A request containing id->name tuples for available users
   * and groups.
   * @param users a seq of id -> name tuples for users on the system
   * @param groups a seq of id -> name tuples for users on the system
   * @param user a profile
   * @param request the underlying request
   * @tparam A the type of the underlying request
   */
  case class UserGroupsRequest[A](
    users: Seq[(String,String)],
    groups: Seq[(String,String)],
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithUser

  case class CreateRequest[A](
    formOrItem: Either[(Form[F],Form[Seq[String]]),MT],
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithUser

  protected def NewItemAction(implicit ct: ContentType[MT]): ActionBuilder[UserGroupsRequest, AnyContent] =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new CoreActionTransformer[WithUserRequest, UserGroupsRequest] {
      override protected def transform[A](request: WithUserRequest[A]): Future[UserGroupsRequest[A]] = {
        dataHelpers.getUserAndGroupList.map { case (users, groups) =>
          UserGroupsRequest(users, groups, request.user, request)
        }
      }
    }

  protected def CreateItemAction(form: Form[F], pf: Request[_] => Map[String,Seq[String]] = _ => Map.empty)(
    implicit fmt: Writable[F], ct: ContentType[MT]): ActionBuilder[CreateRequest, AnyContent] =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new CoreActionTransformer[WithUserRequest, CreateRequest] {
      def transform[A](request: WithUserRequest[A]): Future[CreateRequest[A]] = {
        implicit val req = request
        val visForm = VisibilityForm.form.bindFromRequest
        form.bindFromRequest.fold(
          errorForm => immediate(CreateRequest(Left((errorForm, visForm)), request.user, request.request)),
          doc => {
            val accessors = visForm.value.getOrElse(Nil)
            userDataApi.create(doc, accessors, params = pf(request), logMsg = getLogMessage).map { item =>
              CreateRequest(Right(item), request.user, request)
            } recover {
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
              case ValidationError(errorSet) =>
                val filledForm = doc.getFormErrors(errorSet, form.fill(doc))
                CreateRequest(Left((filledForm, visForm)), request.user, request)
            }
          }
        )
      }
    }
}

