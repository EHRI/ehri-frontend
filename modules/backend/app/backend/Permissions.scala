package backend

import scala.concurrent.Future
import utils.{Page, PageParams}
import acl.{ItemPermissionSet, GlobalPermissionSet}
import defines.ContentTypes


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Permissions {
  def getItemPermissions(userId: String, contentType: ContentTypes.Value, id: String): Future[ItemPermissionSet]

  def setItemPermissions(userId: String, contentType: ContentTypes.Value, id: String, data: Seq[String]): Future[ItemPermissionSet]

  def setGlobalPermissions(userId: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet]

  def getGlobalPermissions(userId: String): Future[GlobalPermissionSet]

  def getScopePermissions(userId: String, id: String): Future[GlobalPermissionSet]

  def setScopePermissions(userId: String, id: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet]

  def listPermissionGrants[A](userId: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]]

  def listItemPermissionGrants[A](id: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]]

  def listScopePermissionGrants[A](id: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]]

  def addGroup[GT,UT](groupId: String, userId: String)(implicit gr: Resource[GT], ur: Resource[UT]): Future[Boolean]

  def removeGroup[GT,UT](groupId: String, userId: String)(implicit gr: Resource[GT], ur: Resource[UT]): Future[Boolean]
}
