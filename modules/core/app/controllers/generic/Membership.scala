package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import backend.rest.RestHelpers
import backend.BackendContentType
import defines.PermissionType
import models.{Group, UserProfile}
import models.base.Accessor
import play.api.mvc.{ActionTransformer, Request, WrappedRequest}

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Membership[MT <: Accessor] extends Read[MT] {

  case class MembershipRequest[A](
    item: MT,
    groups: Seq[(String,String)],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class ManageGroupRequest[A](
    item: MT,
    group: Group,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  def MembershipAction(id: String)(implicit ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, MembershipRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[MembershipRequest[A]] = {
        RestHelpers.getGroupList.map { groups =>
          // filter out the groups the user already belongs to
          val filteredGroups = groups.filter(t => t._1 != request.item.id).filter {
            case (ident, name) =>
              // if the user is admin, they can add the user to ANY group
              if (request.userOpt.exists(_.isAdmin)) {
                !request.item.groups.map(_.id).contains(ident)
              } else {
                // if not, they can add the user to groups they belong to
                // TODO: Enforce these policies with the permission system!
                // TODO: WRITE TESTS FOR THESE WEIRD BEHAVIOURS!!!
                (!request.item.groups.map(_.id).contains(ident)) &&
                  request.userOpt.exists(_.groups.map(_.id).contains(ident))
              }
          }
          MembershipRequest(request.item, filteredGroups, request.userOpt, request)
        }
      }
    }

  def CheckManageGroupAction(id: String, groupId: String)(implicit ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest,ManageGroupRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ManageGroupRequest[A]] = {
        implicit val req = request
        backendHandle.get[Group](groupId).map { group =>
          ManageGroupRequest(request.item, group, request.userOpt, request)
        }
      }
    }

  def AddToGroupAction(id: String, groupId: String)(implicit ct: BackendContentType[MT]) =
    MustBelongTo(groupId) andThen WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req = request
        backendHandle.addGroup[Group, MT](groupId, id).map(_ => request)
      }
    }

  def RemoveFromGroupAction(id: String, groupId: String)(implicit ct: BackendContentType[MT]) =
    MustBelongTo(groupId) andThen WithItemPermissionAction(id, PermissionType.Grant) andThen new ActionTransformer[ItemPermissionRequest, ItemPermissionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemPermissionRequest[A]] = {
        implicit val req = request
        backendHandle.removeGroup[Group, MT](groupId, id).map(_ => request)
      }
    }
}
