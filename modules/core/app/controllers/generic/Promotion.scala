package controllers.generic

import backend.{BackendContentType, BackendReadable}
import defines.PermissionType
import models.UserProfile
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * Trait for handling promotion/demotion on any item.
 */
trait Promotion[MT] extends Generic[MT] {

  self: Read[MT] =>

  protected def EditPromotionAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = WithItemPermissionAction(id, PermissionType.Promote)

  private[generic] def PromoteItemTransformer(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
      implicit val userOpt = request.profileOpt
      backend.promote(id).map { updated =>
        ItemPermissionRequest(updated, request.profileOpt, request)
      }
    }
  }

  private[generic] def RemovePromotionTransformer(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
      implicit val userOpt = request.profileOpt
      backend.removePromotion(id).map { updated =>
        ItemPermissionRequest(updated, request.profileOpt, request)
      }
    }
  }

  private[generic] def DemoteItemTransformer(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
      implicit val userOpt = request.profileOpt
      backend.demote(id).map { updated =>
        ItemPermissionRequest(updated, request.profileOpt, request)
      }
    }
  }

  private[generic] def RemoveDemotionTransformer(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
    override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
      implicit val userOpt = request.profileOpt
      backend.removeDemotion(id).map { updated =>
        ItemPermissionRequest(updated, request.profileOpt, request)
      }
    }
  }

  protected def PromoteItemAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen PromoteItemTransformer(id)

  protected def RemovePromotionAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen RemovePromotionTransformer(id)

  protected def DemoteItemAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen DemoteItemTransformer(id)

  protected def RemoveDemotionAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen RemoveDemotionTransformer(id)

  @deprecated(message = "Use EditPromotionAction instead", since = "1.0.2")
  def promoteAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Promote) { item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  @deprecated(message = "Use PromoteItemAction instead", since = "1.0.2")
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

  @deprecated(message = "Use RemovePromotionAction instead", since = "1.0.2")
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

  @deprecated(message = "Use DemoteItemAction instead", since = "1.0.2")
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

  @deprecated(message = "Use RemoveDemotionAction instead", since = "1.0.2")
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

