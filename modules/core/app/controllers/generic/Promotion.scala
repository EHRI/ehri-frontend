package controllers.generic

import backend.{ContentType, Readable}
import defines.PermissionType
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

/**
 * Trait for handling promotion/demotion on any item.
 */
trait Promotion[MT] extends Generic {

  this: Read[MT] =>

  protected def EditPromotionAction(id: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Promote)

  protected def PromoteItemAction(id: String)(implicit ct: ContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        userDataApi.promote(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }

  protected def RemovePromotionAction(id: String)(implicit ct: ContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        userDataApi.removePromotion(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }

  protected def DemoteItemAction(id: String)(implicit ct: ContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        userDataApi.demote(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }

  protected def RemoveDemotionAction(id: String)(implicit ct: ContentType[MT]) =
    EditPromotionAction(id) andThen new ActionTransformer[ItemPermissionRequest,ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val userOpt = request.userOpt
        userDataApi.removeDemotion(id).map { updated =>
          ItemPermissionRequest(updated, request.userOpt, request)
        }
      }
    }
}

