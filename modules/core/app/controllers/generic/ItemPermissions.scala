package controllers.generic

import acl.ItemPermissionSet
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import utils.{Page, PageParams}
import backend.{Readable, BackendContentType}

import scala.concurrent.Future

/**
 * Trait for setting permissions on an individual item.
 */
trait ItemPermissions[MT] extends Visibility[MT] {

  case class ItemPermissionGrantRequest[A](
    item: MT,
    permissionGrants: Page[PermissionGrant],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser
  
  case class SetItemPermissionRequest[A](
    item: MT,
    accessor: Accessor,
    itemPermissions: ItemPermissionSet,                                      
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  protected def WithGrantPermission(id: String)(implicit ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant)

  protected def PermissionGrantAction(id: String)(implicit ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, ItemPermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionGrantRequest[A]] = {
        implicit val req = request
        val params = PageParams.fromRequest(request)
        userBackend.listItemPermissionGrants[PermissionGrant](id, params).map { permGrants =>
          ItemPermissionGrantRequest(request.item, permGrants, request.userOpt, request)
        }
      }
    }
  
  protected def EditItemPermissionsAction(id: String)(implicit ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen EditVisibilityAction(id)
  
  protected def CheckUpdateItemPermissionsAction(id: String, userType: EntityType.Value, userId: String)(implicit ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, SetItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetItemPermissionRequest[A]] = {
        implicit val req = request
        val accessorF = userBackend.get[Accessor](Accessor.resourceFor(userType), userId)
        val permsF = userBackend.getItemPermissions(userId, ct.contentType, id)
        for {
          accessor <- accessorF
          perms <- permsF
        } yield SetItemPermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }

  protected def getData[A](request: Request[A]): Option[Map[String,Seq[String]]] = request.body match {
    case any: AnyContentAsFormUrlEncoded => Some(any.asFormUrlEncoded.getOrElse(Map.empty))
    case json: AnyContentAsJson => Some(json.asJson.flatMap(_.asOpt[Map[String,Seq[String]]]).getOrElse(Map.empty))
    case _ => None
  }

  protected def UpdateItemPermissionsAction(id: String, userType: EntityType.Value, userId: String)(implicit ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, SetItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetItemPermissionRequest[A]] = {
        implicit val req = request
        val data = getData(request).getOrElse(Map.empty)
        val perms: Seq[String] = data.getOrElse(ct.contentType.toString, Seq.empty)
        for {
          accessor <- userBackend.get[Accessor](Accessor.resourceFor(userType), userId)
          perms <- userBackend.setItemPermissions(userId, ct.contentType, id, perms)
        } yield SetItemPermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }
}

