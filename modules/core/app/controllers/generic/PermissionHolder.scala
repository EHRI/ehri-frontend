package controllers.generic

import defines.{ContentTypes, PermissionType}
import acl.GlobalPermissionSet
import models.base.Accessor
import play.api.mvc._
import models._

import play.api.libs.concurrent.Execution.Implicits._
import utils.{Page, PageParams}
import backend.{BackendReadable, BackendContentType}

import scala.concurrent.Future

/**
 * Trait for managing permissions on Accessor models that can have permissions assigned to them.
 */
trait PermissionHolder[MT <: Accessor] extends Read[MT] {

  case class HolderPermissionGrantRequest[A](
    item: MT,
    permissionGrants: Page[PermissionGrant],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class GlobalPermissionSetRequest[A](
    item: MT,
    permissions: GlobalPermissionSet,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class PermissionGrantRequest[A](
    item: MT,
    permissionGrant: PermissionGrant,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser


  def GrantListAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, HolderPermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[HolderPermissionGrantRequest[A]] = {
        implicit val req = request
        val params = PageParams.fromRequest(request)
        backend.listPermissionGrants[PermissionGrant](id, params).map { perms =>
          HolderPermissionGrantRequest(request.item, perms, request.userOpt, request)
        }
      }
    }

  private def getData[A](request: Request[A]): Option[Map[String,Seq[String]]] = request.body match {
    case any: AnyContentAsFormUrlEncoded => Some(any.asFormUrlEncoded.getOrElse(Map.empty))
    case json: AnyContentAsJson => Some(json.asJson.flatMap(_.asOpt[Map[String,Seq[String]]]).getOrElse(Map.empty))
    case _ => None
  }

  def CheckGlobalPermissionsAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, GlobalPermissionSetRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[GlobalPermissionSetRequest[A]] = {
        implicit val req = request
        backend.getGlobalPermissions(id).map { perms =>
          GlobalPermissionSetRequest(request.item, perms, request.userOpt, request)
        }
      }
    }

  def SetGlobalPermissionsAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, GlobalPermissionSetRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[GlobalPermissionSetRequest[A]] = {
        implicit val req = request
        val data = getData(request).getOrElse(Map.empty)
        val perms: Map[String, List[String]] = ContentTypes.values.toList.map { ct =>
          (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List.empty))
        }.toMap
        backend.setGlobalPermissions(id, perms).map { perms =>
          GlobalPermissionSetRequest(request.item, perms, request.userOpt, request)
        }
      }
    }

  def CheckRevokePermissionAction(id: String, permId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, PermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[PermissionGrantRequest[A]] = {
        implicit val req = request
        backend.get[PermissionGrant](permId).map { perm =>
          PermissionGrantRequest(request.item, perm, request.userOpt, request)
        }
      }
    }

  def RevokePermissionAction(id: String, permId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req = request
        backend.delete(permId).map( _ => request)
      }
    }
}
