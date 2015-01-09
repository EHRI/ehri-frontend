package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{Page, PageParams}
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

  def listPermissionGrants[A](userId: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def listItemPermissionGrants[A](id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def listScopePermissionGrants[A](id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def addGroup[GT,UT](groupId: String, userId: String)(implicit apiUser: ApiUser, gr: BackendResource[GT], ur: BackendResource[UT], executionContext: ExecutionContext): Future[Boolean]

  def removeGroup[GT,UT](groupId: String, userId: String)(implicit apiUser: ApiUser, gr: BackendResource[GT], ur: BackendResource[UT], executionContext: ExecutionContext): Future[Boolean]
}
