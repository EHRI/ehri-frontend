package controllers.generic

import services.data._
import defines.PermissionType
import forms.VisibilityForm
import models.{UserProfile, UsersAndGroups}
import models.base.{MetaModel, Model, Persistable}
import play.api.data._
import play.api.mvc._

import scala.concurrent.Future

/**
  * Controller trait for creating [[models.base.Accessible]] ites..
  */
trait Create[F <: Model with Persistable, MT <: MetaModel[F]] extends Write {

  this: Read[MT] =>

  protected def dataHelpers: DataHelpers

  /**
    * A request containing id->name tuples for available users
    * and groups.
    *
    * @param usersAndGroups data about user and groups
    * @param user           a profile
    * @param request        the underlying request
    * @tparam A the type of the underlying request
    */
  case class UserGroupsRequest[A](
    usersAndGroups: UsersAndGroups,
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithUser

  case class CreateRequest[A](
    formOrItem: Either[(Form[F], Form[Seq[String]], UsersAndGroups), MT],
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithUser

  protected def NewItemAction(implicit ct: ContentType[MT]): ActionBuilder[UserGroupsRequest, AnyContent] =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new CoreActionTransformer[WithUserRequest, UserGroupsRequest] {
      override protected def transform[A](request: WithUserRequest[A]): Future[UserGroupsRequest[A]] = {
        dataHelpers.getUserAndGroupList.map { usersAndGroups =>
          UserGroupsRequest(usersAndGroups, request.user, request)
        }
      }
    }

  protected def CreateItemAction(form: Form[F], pf: Request[_] => Map[String, Seq[String]] = _ => Map.empty)(
    implicit fmt: Writable[F], ct: ContentType[MT]): ActionBuilder[CreateRequest, AnyContent] =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new CoreActionTransformer[WithUserRequest, CreateRequest] {
      def transform[A](request: WithUserRequest[A]): Future[CreateRequest[A]] = {
        implicit val req: WithUserRequest[A] = request
        val visForm = VisibilityForm.form.bindFromRequest
        form.bindFromRequest.fold(
          errorForm => dataHelpers.getUserAndGroupList.map { usersAndGroups =>
            CreateRequest(Left((errorForm, visForm, usersAndGroups)), request.user, request.request)
          },
          doc => {
            val accessors = visForm.value.getOrElse(Nil)
            userDataApi.create(doc, accessors, params = pf(request), logMsg = getLogMessage).map { item =>
              CreateRequest(Right(item), request.user, request)
            } recoverWith {
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
              case ValidationError(errorSet) =>
                val filledForm = doc.getFormErrors(errorSet, form.fill(doc))
                dataHelpers.getUserAndGroupList.map { usersAndGroups =>
                  CreateRequest(Left((filledForm, visForm, usersAndGroups)), request.user, request)
                }
            }
          }
        )
      }
    }
}

