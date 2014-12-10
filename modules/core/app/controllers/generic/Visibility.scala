package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.UserProfile
import backend.{BackendReadable, BackendContentType}
import scala.concurrent.Future

/**
 * Trait for setting visibility on any item.
 */
trait Visibility[MT] extends Generic[MT] {

  def visibilityAction(id: String)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update) { item => implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(item)(users)(groups)(userOpt)(request)
      }
    }
  }

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

