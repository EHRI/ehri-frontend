package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import backend.{EventHandler, Social, ApiUser}
import scala.concurrent.Future
import utils.ListParams
import models.UserProfile
import defines.EntityType
import models.json.RestReadable
import play.api.libs.json.Reads
import models.base.AnyModel
import play.api.cache.Cache

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestSocial extends Social with RestDAO {

  import play.api.Play.current
  val eventHandler: EventHandler

  private def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.UserProfile)

  private def followUrl(userId: String, otherId: String) = enc(requestUrl, userId, "follow", otherId)
  private def watchUrl(userId: String, otherId: String) = enc(requestUrl, userId, "watch", otherId)
  private def followingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isFollowing", otherId)
  private def watchingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isWatching", otherId)

  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(followUrl(userId, otherId)).post("").map { r =>
      checkError(r)
      Cache.set(followingUrl(userId, otherId), true)
    }
  }
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(followUrl(userId, otherId)).delete().map { r =>
      checkError(r)
      Cache.set(followingUrl(userId, otherId), false)
    }
  }
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    val url = watchingUrl(userId, otherId)
    val cached = Cache.getAs[Boolean](url)
    if (cached.isDefined) {
      Future.successful(cached.get)
    } else {
      userCall(followingUrl(userId, otherId)).get().map { r =>
        val bool = checkErrorAndParse[Boolean](r)
        Cache.set(followingUrl(userId, otherId), bool)
        bool
      }
    }
  }

  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  def listFollowers(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "followers")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def listFollowing(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "following")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def listWatching(userId: String, params: ListParams)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel]): Future[List[AnyModel]] = {
    userCall(enc(requestUrl, userId, "watching")).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(watchUrl(userId, otherId)).post("").map { r =>
      Cache.set(watchingUrl(userId, otherId), true)
      checkError(r)
    }
  }

  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(watchUrl(userId, otherId)).delete().map { r =>
      Cache.set(watchingUrl(userId, otherId), false)
      checkError(r)
    }
  }

  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    val url = watchingUrl(userId, otherId)
    val cached = Cache.getAs[Boolean](url)
    if (cached.isDefined) {
      Future.successful(cached.get)
    } else {
      userCall(url).get().map { r =>
        val bool = checkErrorAndParse[Boolean](r)
        Cache.set(url, bool)
        bool
      }
    }
  }
}


case class SocialDAO(eventHandler: EventHandler) extends RestSocial