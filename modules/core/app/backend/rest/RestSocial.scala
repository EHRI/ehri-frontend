package backend.rest

import backend.{Page, EventHandler, Social, ApiUser}
import scala.concurrent.{ExecutionContext, Future}
import utils.{FutureCache, PageParams, ListParams}
import models.{VirtualUnit, Link, Annotation, UserProfile}
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
  private def blockedUrl(userId: String) = enc(requestUrl, userId, "blocked")
  private def blockUrl(userId: String, otherId: String) = enc(requestUrl, userId, "block", otherId)
  private def isBlockingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isBlocking", otherId)

  def follow(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(followUrl(userId, otherId)).post("").map { r =>
      checkError(r)
      Cache.set(isFollowingUrl(userId, otherId), true, cacheTime)
      Cache.remove(followingUrl(userId))
      Cache.remove(userId)
    }
  }
  def unfollow(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(followUrl(userId, otherId)).delete().map { r =>
      checkError(r)
      Cache.set(isFollowingUrl(userId, otherId), false, cacheTime)
      Cache.remove(followingUrl(userId))
      Cache.remove(userId)
    }
  }
  def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = isFollowingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  def listFollowers(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "followers")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def pageFollowers(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[Page[UserProfile]] = {
    userCall(enc(requestUrl, userId, "followers", "page")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(rd.restReads))
    }
  }

  def listFollowing(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[List[UserProfile]] = {
    userCall(enc(requestUrl, userId, "following")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def pageFollowing(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[UserProfile], executionContext: ExecutionContext): Future[Page[UserProfile]] = {
    userCall(enc(requestUrl, userId, "following", "page")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(rd.restReads))
    }
  }

  def listWatching(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel], executionContext: ExecutionContext): Future[List[AnyModel]] = {
    userCall(enc(requestUrl, userId, "watching")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def pageWatching(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel], executionContext: ExecutionContext): Future[Page[AnyModel]] = {
    userCall(enc(requestUrl, userId, "watching", "page")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(rd.restReads))
    }
  }

  def watch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(watchUrl(userId, otherId)).post("").map { r =>
      Cache.set(isWatchingUrl(userId, otherId), true, cacheTime)
      Cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(watchUrl(userId, otherId)).delete().map { r =>
      Cache.set(isWatchingUrl(userId, otherId), false, cacheTime)
      Cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = isWatchingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  def listBlocked(userId: String, params: ListParams = ListParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel], executionContext: ExecutionContext): Future[List[AnyModel]] = {
    userCall(enc(requestUrl, userId, "blocked")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Reads.list(rd.restReads))
    }
  }

  def pageBlocked(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: RestReadable[AnyModel], executionContext: ExecutionContext): Future[Page[AnyModel]] = {
    userCall(enc(requestUrl, userId, "blocked", "page")).withQueryString(params.toSeq: _*).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(rd.restReads))
    }
  }

  def block(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(blockUrl(userId, otherId)).post("").map { r =>
      Cache.set(isBlockingUrl(userId, otherId), true, cacheTime)
      Cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  def unblock(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(blockUrl(userId, otherId)).delete().map { r =>
      Cache.set(isBlockingUrl(userId, otherId), false, cacheTime)
      Cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  def isBlocking(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = isBlockingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  def userAnnotations(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Annotation]] = {
    userCall(enc(requestUrl, userId, EntityType.Annotation, "page")).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(Annotation.Converter.restReads))
    }
  }

  def userLinks(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Link]] = {
    userCall(enc(requestUrl, userId, EntityType.Link, "page")).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(Link.Converter.restReads))
    }
  }

  def userBookmarks(userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[VirtualUnit]] = {
    userCall(enc(requestUrl, EntityType.VirtualUnit, "forUser", userId)).get().map { r =>
      checkErrorAndParse(r)(Page.pageReads(VirtualUnit.Converter.restReads))
    }
  }
}


case class SocialDAO(eventHandler: EventHandler) extends RestSocial