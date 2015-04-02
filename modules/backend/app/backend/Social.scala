package backend

import scala.concurrent.Future
import utils.{Page, PageParams}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow[U: Resource](userId: String, otherId: String): Future[Unit]

  def unfollow[U: Resource](userId: String, otherId: String): Future[Unit]

  def isFollowing(userId: String, otherId: String): Future[Boolean]

  def isFollower(userId: String, otherId: String): Future[Boolean]

  def followers[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]]

  def following[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]]

  def watching[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  def watch(userId: String, otherId: String): Future[Unit]

  def unwatch(userId: String, otherId: String): Future[Unit]

  def isWatching(userId: String, otherId: String): Future[Boolean]

  def blocked[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  def block(userId: String, otherId: String): Future[Unit]

  def unblock(userId: String, otherId: String): Future[Unit]

  def isBlocking(userId: String, otherId: String): Future[Boolean]

  def userAnnotations[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  def userLinks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]

  def userBookmarks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]]
}
