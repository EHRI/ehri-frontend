package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{Page, PageParams}
import models._
import acl.{ItemPermissionSet, GlobalPermissionSet}
import defines.ContentTypes


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Permissions {
  def getItemPermissions(userId: String, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[ItemPermissionSet]

  def setItemPermissions(userId: String, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[ItemPermissionSet]

  def setGlobalPermissions(userId: String, data: Map[String, List[String]])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet]

  def getGlobalPermissions(userId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet]

  def getScopePermissions(userId: String, id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet]

  def setScopePermissions(userId: String, id: String, data: Map[String, List[String]])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet]

  def listPermissionGrants(userId: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[PermissionGrant], executionContext: ExecutionContext): Future[Page[PermissionGrant]]

  def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[PermissionGrant], executionContext: ExecutionContext): Future[Page[PermissionGrant]]

  def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[PermissionGrant], executionContext: ExecutionContext): Future[Page[PermissionGrant]]

  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]
}
