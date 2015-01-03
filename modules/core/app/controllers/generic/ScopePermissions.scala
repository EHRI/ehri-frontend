package controllers.generic

import acl.GlobalPermissionSet
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import utils.{Page, PageParams}
import backend.{BackendReadable, BackendContentType}

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
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalProfile

  case class SetScopePermissionRequest[A](
    item: MT,
    accessor: Accessor,
    scopePermissions: GlobalPermissionSet,
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
  with WithOptionalProfile

  protected def ScopePermissionGrantAction(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, ScopePermissionGrantRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ScopePermissionGrantRequest[A]] = {
        implicit val req = request
        val itemParams = PageParams.fromRequest(request)
        val scopeParams = PageParams.fromRequest(request, namespace = "s")
        for {
          permGrants <- backend.listItemPermissionGrants[PermissionGrant](id, itemParams)
          scopeGrants <- backend.listScopePermissionGrants[PermissionGrant](id, scopeParams)
        } yield ScopePermissionGrantRequest(request.item, permGrants, scopeGrants, request.profileOpt, request)
      }
    }

  protected def CheckUpdateScopePermissionsAction(id: String, userType: EntityType.Value, userId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, SetScopePermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetScopePermissionRequest[A]] = {
        implicit val req = request
        val accessorF = backend.get[Accessor](Accessor.resourceFor(userType), userId)
        val permsF = backend.getScopePermissions(userId, id)
        for {
          accessor <- accessorF
          perms <- permsF
        } yield SetScopePermissionRequest(request.item, accessor, perms, request.profileOpt, request)
      }
    }


  protected def UpdateScopePermissionsAction(id: String, userType: EntityType.Value, userId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithGrantPermission(id) andThen new ActionTransformer[ItemPermissionRequest, SetScopePermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[SetScopePermissionRequest[A]] = {
        implicit val req = request
        val data = getData(request).getOrElse(Map.empty)
        val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
          (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
        }.toMap
        for {
          accessor <- backend.get[Accessor](Accessor.resourceFor(userType), userId)
          perms <- backend.setScopePermissions(userId, id, perms)
        } yield SetScopePermissionRequest(request.item, accessor, perms, request.profileOpt, request)
      }
    }


  @deprecated(message = "Use ScopePermissionGrantAction instead", since = "1.0.2")
  def manageScopedPermissionsAction(id: String)(
      f: MT => Page[PermissionGrant] => Page[PermissionGrant]=> Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val itemParams = PageParams.fromRequest(request)
      val scopeParams = PageParams.fromRequest(request, namespace = "s")
      for {
        permGrants <- backend.listItemPermissionGrants[PermissionGrant](id, itemParams)
        scopeGrants <- backend.listScopePermissionGrants[PermissionGrant](id, scopeParams)
      } yield f(item)(permGrants)(scopeGrants)(userOpt)(request)
    }
  }

  @deprecated(message = "Use CheckUpdateScopePermissionsAction instead", since = "1.0.2")
  def setScopedPermissionsAction(id: String, userType: EntityType.Value, userId: String)(
      f: MT => Accessor => acl.GlobalPermissionSet => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      for {
        accessor <- backend.get[Accessor](Accessor.resourceFor(userType), userId)
        perms <- backend.getScopePermissions(userId, id)
      } yield f(item)(accessor)(perms)(userOpt)(request)
    }
  }

  @deprecated(message = "Use UpdateScopePermissionsAction instead", since = "1.0.2")
  def setScopedPermissionsPostAction(id: String, userType: EntityType.Value, userId: String)(
      f: acl.GlobalPermissionSet => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Grant) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap

      for {
        accessor <- backend.get[Accessor](Accessor.resourceFor(userType), userId)
        perms <- backend.setScopePermissions(userId, id, perms)
      } yield f(perms)(userOpt)(request)
    }
  }
}

