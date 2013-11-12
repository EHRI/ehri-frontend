package backend

import scala.concurrent.Future
import utils.PageParams
import models._
import models.base.Accessor
import acl.{ItemPermissionSet, GlobalPermissionSet}
import defines.ContentTypes

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Permissions extends Generic with Descriptions with Links with Annotations with Events {
  def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]]
  def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]]
  def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]]
  def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
  def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
  def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]]
  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean]
}
