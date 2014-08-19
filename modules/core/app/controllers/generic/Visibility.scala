package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.UserProfile
import backend.{BackendReadable, BackendContentType}

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

  def visibilityPostAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update) { item => implicit userOpt => implicit request =>
      val data = forms.VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
      backend.setVisibility[MT](id, data).map { item =>
        f(item)(userOpt)(request)
      }
    }
  }

  def promoteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Promote) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def promotePostAction(id: String)(f: MT => Boolean => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Promote) { item => implicit userOpt => implicit request =>
      backend.promote(id).map { bool =>
        f(item)(bool)(userOpt)(request)
      }
    }
  }

  def demoteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Promote) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def demotePostAction(id: String)(f: MT => Boolean => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Promote) { item => implicit userOpt => implicit request =>
      backend.demote(id).map { bool =>
        f(item)(bool)(userOpt)(request)
      }
    }
  }
}

