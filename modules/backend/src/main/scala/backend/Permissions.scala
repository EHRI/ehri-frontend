package backend

import scala.concurrent.Future
import utils.{Page, PageParams}
import acl.{ItemPermissionSet, GlobalPermissionSet}
import defines.ContentTypes


trait Permissions {
  def getItemPermissions(userId: String, contentType: ContentTypes.Value, id: String): Future[ItemPermissionSet]

  def setItemPermissions(userId: String, contentType: ContentTypes.Value, id: String, data: Seq[String]): Future[ItemPermissionSet]

  def setGlobalPermissions(userId: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet]

  def getGlobalPermissions(userId: String): Future[GlobalPermissionSet]

  def getScopePermissions(userId: String, id: String): Future[GlobalPermissionSet]

  def setScopePermissions(userId: String, id: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet]

  def listPermissionGrants[A: Readable](userId: String, params: PageParams): Future[Page[A]]

  def listItemPermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]]

  def listScopePermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]]

  def addGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Boolean]

  def removeGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Boolean]
}
