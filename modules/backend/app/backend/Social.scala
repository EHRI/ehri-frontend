package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{Page, PageParams}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow[U](userId: String, otherId: String)(implicit apiUser: ApiUser, rs: BackendResource[U], executionContext: ExecutionContext): Future[Unit]

  def unfollow[U](userId: String, otherId: String)(implicit apiUser: ApiUser, rs: BackendResource[U], executionContext: ExecutionContext): Future[Unit]

  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def followers[U](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[U], executionContext: ExecutionContext): Future[Page[U]]

  def following[U](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[U], executionContext: ExecutionContext): Future[Page[U]]

  def watching[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def blocked[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def block(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def unblock(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]

  def isBlocking(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def userAnnotations[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def userLinks[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def userBookmarks[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def addBookmark[MT](setId: String, id: String)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit]

  def deleteBookmarks[MT](setId: String, ids: Seq[String])(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit]

  def moveBookmarks[MT](fromSet: String, toSet: String, ids: Seq[String])(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit]
}
