package controllers.generic

import backend.ContentType
import backend.rest.DataHelpers
import defines.PermissionType
import models.UserProfile
import play.api.mvc._

import scala.concurrent.Future

/**
  * Trait for setting visibility on any item.
  */
trait Visibility[MT] extends Read[MT] {

  protected def dataHelpers: DataHelpers

  case class VisibilityRequest[A](
    item: MT,
    users: Seq[(String, String)],
    groups: Seq[(String, String)],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def EditVisibilityAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[VisibilityRequest] =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest, VisibilityRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[VisibilityRequest[A]] = {
        dataHelpers.getUserAndGroupList.map { case (users, groups) =>
          VisibilityRequest(request.item, users, groups, request.userOpt, request)
        }
      }
    }

  protected def UpdateVisibilityAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemPermissionRequest] =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest, ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req = request
        val data = forms.VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
        userDataApi.setVisibility(id, data).map { newItem =>
          ItemPermissionRequest(newItem, request.userOpt, request)
        }
      }
    }
}

