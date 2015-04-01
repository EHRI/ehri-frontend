package backend

import scala.concurrent.Future
import utils.{Page, PageParams}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow[U](userId: String, otherId: String)(implicit rs: Resource[U]): Future[Unit]

  def unfollow[U](userId: String, otherId: String)(implicit rs: Resource[U]): Future[Unit]

  def isFollowing(userId: String, otherId: String): Future[Boolean]

  def isFollower(userId: String, otherId: String): Future[Boolean]

  def followers[U](userId: String, params: PageParams = PageParams.empty)(implicit rd: Readable[U]): Future[Page[U]]

  def following[U](userId: String, params: PageParams = PageParams.empty)(implicit rd: Readable[U]): Future[Page[U]]

  def watching[A](userId: String, params: PageParams = PageParams.empty)(implicit rd: Readable[A]): Future[Page[A]]

  def watch(userId: String, otherId: String): Future[Unit]

  def unwatch(userId: String, otherId: String): Future[Unit]

  def isWatching(userId: String, otherId: String): Future[Boolean]

  def blocked[A](userId: String, params: PageParams = PageParams.empty)(implicit rd: Readable[A]): Future[Page[A]]

  def block(userId: String, otherId: String): Future[Unit]

  def unblock(userId: String, otherId: String): Future[Unit]

  def isBlocking(userId: String, otherId: String): Future[Boolean]

  def userAnnotations[A](userId: String, params: PageParams = PageParams.empty)(implicit rd: Readable[A]): Future[Page[A]]

  def userLinks[A](userId: String, params: PageParams = PageParams.empty)(implicit rd: Readable[A]): Future[Page[A]]

  def userBookmarks[A](userId: String, params: PageParams = PageParams.empty)(implicit rd: Readable[A]): Future[Page[A]]
}
