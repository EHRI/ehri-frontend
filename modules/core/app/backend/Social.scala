package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{Page, PageParams}
import models.{VirtualUnit, Link, Annotation, UserProfile}
import models.base.AnyModel


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def followers(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[UserProfile], executionContext: ExecutionContext): Future[Page[UserProfile]]

  def following(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[UserProfile], executionContext: ExecutionContext): Future[Page[UserProfile]]

  def watching(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[AnyModel], executionContext: ExecutionContext): Future[Page[AnyModel]]

  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def blocked(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[AnyModel], executionContext: ExecutionContext): Future[Page[AnyModel]]

  def block(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def unblock(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def isBlocking(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def userAnnotations(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Annotation]]

  def userLinks(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Link]]

  def userBookmarks(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[VirtualUnit]]

  def addBookmark(setId: String, id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def deleteBookmark(setId: String, id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def moveBookmarks(fromSet: String, toSet: String, ids: Seq[String])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]
}
