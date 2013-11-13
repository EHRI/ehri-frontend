package backend

import scala.concurrent.Future
import utils.ListParams
import models.UserProfile
import models.json.RestReadable

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow(userId: String)(implicit apiUser: ApiUser): Future[Unit]
  def unfollow(userId: String)(implicit apiUser: ApiUser): Future[Unit]
  def isFollowing(userId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def isFollower(userId: String)(implicit apiUser: ApiUser): Future[Boolean]
  def listFollowers(params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]]
  def listFollowing(params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]]
}
