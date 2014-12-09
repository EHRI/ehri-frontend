package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import defines.PermissionType
import models.UserProfile
import backend.{BackendReadable, BackendContentType}
import scala.concurrent.Future

/**
 * Trait for handling promotion/demotion on any item.
 */
trait Promotion[MT] extends Generic[MT] {
  def promoteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Promote) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  object promotePostAction {
    def async(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Promote) { _ => implicit userOpt => implicit request =>
        backend.promote(id).flatMap { updated =>
          f(updated)(userOpt)(request)
        }
      }
    }

    def apply(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }

  object removePromotionPostAction {
    def async(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Promote) { _ => implicit userOpt => implicit request =>
        backend.removePromotion(id).flatMap { updated =>
          f(updated)(userOpt)(request)
        }
      }
    }

    def apply(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }

  object demotePostAction {
    def async(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Promote) { _ => implicit userOpt => implicit request =>
        backend.demote(id).flatMap { updated =>
          f(updated)(userOpt)(request)
        }
      }
    }

    def apply(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }

  object removeDemotionPostAction {
    def async(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Future[Result])(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      withItemPermission.async[MT](id, PermissionType.Promote) { _ => implicit userOpt => implicit request =>
        backend.removeDemotion(id).flatMap { updated =>
          f(updated)(userOpt)(request)
        }
      }
    }

    def apply(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(t => Future.successful(t)))))
    }
  }
}

