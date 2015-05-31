package controllers.generic

import backend.rest.RestHelpers
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.UserProfile
import backend.ContentType
import scala.concurrent.Future

/**
 * Trait for setting visibility on any item.
 */
trait Visibility[MT] extends Read[MT] {

  case class VisibilityRequest[A](
    item: MT,
    users: Seq[(String,String)],
    groups: Seq[(String,String)],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def EditVisibilityAction(id: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest, VisibilityRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[VisibilityRequest[A]] = {
        for {
          users <- getUserList
          groups <- getGroupList
        } yield VisibilityRequest(request.item, users, groups, request.userOpt, request)
      }
    }

  protected def UpdateVisibilityAction(id: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req = request
        val data = forms.VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
        userBackend.setVisibility(id, data).map { newItem =>
          ItemPermissionRequest(newItem, request.userOpt, request)
        }
      }
    }
}

