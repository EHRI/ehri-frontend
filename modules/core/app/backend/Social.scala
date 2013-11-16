package backend

import scala.concurrent.Future
import utils.{PageParams, ListParams}
import models.UserProfile
import models.json.RestReadable
import models.base.AnyModel

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit]
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit]
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def listFollowers(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]]
  def pageFollowers(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[Page[UserProfile]]
  def listFollowing(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]]
  def pageFollowing(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[Page[UserProfile]]
  def listWatching(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel]): Future[List[AnyModel]]
  def pageWatching(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel]): Future[Page[AnyModel]]
  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit]
  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit]
  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean]
}
