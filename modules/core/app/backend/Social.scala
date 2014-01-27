package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{PageParams, ListParams}
import models.{Link, Annotation, UserProfile}
import models.json.RestReadable
import models.base.AnyModel

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Social {
  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]
  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]
  def listFollowers(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[List[UserProfile]]
  def pageFollowers(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[Page[UserProfile]]
  def listFollowing(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[List[UserProfile]]
  def pageFollowing(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[Page[UserProfile]]
  def listWatching(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel], executionContext: ExecutionContext): Future[List[AnyModel]]
  def pageWatching(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel], executionContext: ExecutionContext): Future[Page[AnyModel]]
  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]
  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit]
  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]

  def userAnnotations(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Annotation]]
  def userLinks(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Link]]
}
