package controllers.generic

import backend.{BackendContentType, BackendReadable}
import defines.PermissionType
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * Trait for handling promotion/demotion on any item.
 */
trait Promotion[MT] extends Generic[MT] {

  this: Read[MT] =>

  protected def EditPromotionAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Promote)

  protected def PromoteItemAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        backend.promote(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }

  protected def RemovePromotionAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        backend.removePromotion(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }

  protected def DemoteItemAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        backend.demote(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }

  protected def RemoveDemotionAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        backend.removeDemotion(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }
}

