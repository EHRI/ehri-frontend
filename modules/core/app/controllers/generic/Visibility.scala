package controllers.generic

import backend.rest.RestHelpers
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.UserProfile
import backend.{BackendReadable, BackendContentType}
import scala.concurrent.Future

/**
 * Trait for setting visibility on any item.
 */
trait Visibility[MT] extends Read[MT] {

  case class VisibilityRequest[A](
    item: MT,
    users: Seq[(String,String)],
    groups: Seq[(String,String)],
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalProfile

  def EditVisibilityAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest, VisibilityRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[VisibilityRequest[A]] = {
        for {
          users <- RestHelpers.getUserList
          groups <- RestHelpers.getGroupList
        } yield VisibilityRequest(request.item, users, groups, request.profileOpt, request)
      }
    }

  def UpdateVisibilityAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req = request
        val data = forms.VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
        backend.setVisibility(id, data).map { newItem =>
          ItemPermissionRequest(newItem, request.profileOpt, request)
        }
      }
    }


  @deprecated(message = "Use EditVisibilityAction instead", since = "1.0.2")
  def visibilityAction(id: String)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update) { item => implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(item)(users)(groups)(userOpt)(request)
      }
    }
  }

  @deprecated(message = "Use UpdateVisibilityAction instead", since = "1.0.2")
  object visibilityPostAction {
    def async(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Update) { item => implicit userOpt => implicit request =>
        val data = forms.VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
        backend.setVisibility[MT](id, data).flatMap { item =>
          f(item)(userOpt)(request)
        }
      }
    }

    def apply(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }
}

