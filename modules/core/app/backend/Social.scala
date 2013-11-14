package backend

import scala.concurrent.Future
import utils.ListParams
import models.UserProfile
import models.json.RestReadable

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit]
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit]
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def listFollowers(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]]
  def listFollowing(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]]
}
