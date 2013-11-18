package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import backend.{Page, EventHandler, Social, ApiUser}
import scala.concurrent.Future
import utils.{PageParams, ListParams}
import models.{Link, Annotation, UserProfile}
import defines.EntityType
import models.json.RestReadable
import play.api.libs.json.{JsValue, Json, Reads}
import models.base.AnyModel
import play.api.cache.Cache

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestSocial extends Social with RestDAO {

  import backend.rest.Constants._
  import play.api.Play.current
  val eventHandler: EventHandler

  private def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, EntityType.UserProfile)

  private def followUrl(userId: String, otherId: String) = enc(requestUrl, userId, "follow", otherId)
  private def followingUrl(userId: String) = enc(requestUrl, userId, "following")
  private def watchUrl(userId: String, otherId: String) = enc(requestUrl, userId, "watch", otherId)
  private def watchingUrl(userId: String) = enc(requestUrl, userId, "watching")
  private def isFollowingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isFollowing", otherId)
  private def isWatchingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isWatching", otherId)

  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(followUrl(userId, otherId)).post("").map { r =>
      checkError(r)
      Cache.set(isFollowingUrl(userId, otherId), true, cacheTime)
      Cache.remove(followingUrl(userId))
      Cache.remove(userId)
    }
  }
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(followUrl(userId, otherId)).delete().map { r =>
      checkError(r)
      Cache.set(isFollowingUrl(userId, otherId), false, cacheTime)
      Cache.remove(followingUrl(userId))
      Cache.remove(userId)
    }
  }
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    val url = isWatchingUrl(userId, otherId)
    val cached = Cache.getAs[Boolean](url)
    if (cached.isDefined) {
      Future.successful(cached.get)
    } else {
      userCall(isFollowingUrl(userId, otherId)).get().map { r =>
        val bool = checkErrorAndParse[Boolean](r)
        Cache.set(isFollowingUrl(userId, otherId), bool, cacheTime)
        bool
      }
    }
  }

  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  def listFollowers(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "followers")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def pageFollowers(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[Page[UserProfile]] = {
    userCall(enc(requestUrl, userId, "followers", "page")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(rd.restReads))
    }
  }

  def listFollowing(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "following")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def pageFollowing(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile]): Future[Page[UserProfile]] = {
    userCall(enc(requestUrl, userId, "following", "page")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(rd.restReads))
    }
  }

  def listWatching(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel]): Future[List[AnyModel]] = {
    userCall(enc(requestUrl, userId, "watching")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def pageWatching(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel]): Future[Page[AnyModel]] = {
    userCall(enc(requestUrl, userId, "watching", "page")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(rd.restReads))
    }
  }

  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(watchUrl(userId, otherId)).post("").map { r =>
      Cache.set(isWatchingUrl(userId, otherId), true, cacheTime)
      Cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Unit] = {
    userCall(watchUrl(userId, otherId)).delete().map { r =>
      Cache.set(isWatchingUrl(userId, otherId), false, cacheTime)
      Cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    val url = isWatchingUrl(userId, otherId)
    val cached = Cache.getAs[Boolean](url)
    if (cached.isDefined) {
      Future.successful(cached.get)
    } else {
      userCall(url).get().map { r =>
        val bool = checkErrorAndParse[Boolean](r)
        Cache.set(url, bool, cacheTime)
        bool
      }
    }
  }

  def userAnnotations(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser): Future[Page[Annotation]] = {
    Future.successful(Page.empty)
  }

  def userLinks(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser): Future[Page[Link]] = {
    Future.successful(Page.empty)
  }
}


case class SocialDAO(eventHandler: EventHandler) extends RestSocial