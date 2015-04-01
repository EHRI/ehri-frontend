package controllers.generic

import acl.GlobalPermissionSet
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import utils.{Page, PageParams}
import backend.BackendContentType

import scala.concurrent.Future

/**
 * Trait for setting visibility on any AccessibleEntity.
 */
trait ScopePermissions[MT] extends ItemPermissions[MT] {

  val targetContentTypes: Seq[ContentTypes.Value]

  case class ScopePermissionGrantRequest[A](
    item: MT,
    permissionGrants: Page[PermissionGrant],
    scopePermissionGrants: Page[PermissionGrant],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  case class SetScopePermissionRequest[A](
    item: MT,
    accessor: Accessor,
    scopePermissions: GlobalPermissionSet,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalUser

  protected def ScopePermissionGrantAction(id: String)(implicit ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, ScopePermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ScopePermissionGrantRequest[A]] = {
        implicit val req = request
        val itemParams = PageParams.fromRequest(request)
        val scopeParams = PageParams.fromRequest(request, namespace = "s")
        for {
          permGrants <- userBackend.listItemPermissionGrants[PermissionGrant](id, itemParams)
          scopeGrants <- userBackend.listScopePermissionGrants[PermissionGrant](id, scopeParams)
        } yield ScopePermissionGrantRequest(request.item, permGrants, scopeGrants, request.userOpt, request)
      }
    }

  protected def CheckUpdateScopePermissionsAction(id: String, userType: EntityType.Value, userId: String)(implicit ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, SetScopePermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetScopePermissionRequest[A]] = {
        implicit val req = request
        val accessorF = userBackend.get[Accessor](Accessor.resourceFor(userType), userId)
        val permsF = userBackend.getScopePermissions(userId, id)
        for {
          accessor <- accessorF
          perms <- permsF
        } yield SetScopePermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }


  protected def UpdateScopePermissionsAction(id: String, userType: EntityType.Value, userId: String)(implicit ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, SetScopePermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetScopePermissionRequest[A]] = {
        implicit val req = request
        val data = getData(request).getOrElse(Map.empty)
        val perms: Map[String, Seq[String]] = targetContentTypes.map { ct =>
          ct.toString -> data.getOrElse(ct.toString, Seq.empty)
        }.toMap
        for {
          accessor <- userBackend.get[Accessor](Accessor.resourceFor(userType), userId)
          perms <- userBackend.setScopePermissions(userId, id, perms)
        } yield SetScopePermissionRequest(request.item, accessor, perms, request.userOpt, request)
      }
    }
}

