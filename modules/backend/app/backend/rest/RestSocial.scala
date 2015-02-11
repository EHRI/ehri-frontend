package backend.rest

import backend._
import scala.concurrent.{ExecutionContext, Future}
import utils.{Page, PageParams}
import defines.EntityType
import play.api.cache.Cache
import caching.FutureCache

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestSocial extends Social with RestDAO {

  import backend.rest.Constants._
  val eventHandler: EventHandler

  private def requestUrl = s"$baseUrl/${EntityType.UserProfile}"

  private def followingUrl(userId: String) = enc(requestUrl, userId, "following")

  private def watchingUrl(userId: String) = enc(requestUrl, userId, "watching")

  private def blockedUrl(userId: String) = enc(requestUrl, userId, "blocked")

  private def isFollowingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isFollowing", otherId)

  private def isWatchingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isWatching", otherId)

  private def isBlockingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isBlocking", otherId)

  override def follow[U](userId: String, otherId: String)(implicit apiUser: ApiUser, rs: BackendResource[U], executionContext: ExecutionContext): Future[Unit] = {
    userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      checkError(r)
      Cache.set(isFollowingUrl(userId, otherId), true, cacheTime)
      Cache.remove(followingUrl(userId))
      Cache.remove(canonicalUrl(userId))
    }
  }

  override def unfollow[U](userId: String, otherId: String)(implicit apiUser: ApiUser, rs: BackendResource[U], executionContext: ExecutionContext): Future[Unit] = {
    userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      checkError(r)
      Cache.set(isFollowingUrl(userId, otherId), false, cacheTime)
      Cache.remove(followingUrl(userId))
      Cache.remove(canonicalUrl(userId))
    }
  }

  override def isFollowing(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = isFollowingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def isFollower(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  override def followers[U](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[U], executionContext: ExecutionContext): Future[Page[U]] = {
    val url: String = enc(requestUrl, userId, "followers")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(rd.restReads)
    }
  }

  override def following[U](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[U], executionContext: ExecutionContext): Future[Page[U]] = {
    val url: String = followingUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(rd.restReads)
    }
  }

  override def watching[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, "watching")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(rd.restReads)
    }
  }

  override def watch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      Cache.set(isWatchingUrl(userId, otherId), true, cacheTime)
      Cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  override def unwatch(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      Cache.set(isWatchingUrl(userId, otherId), false, cacheTime)
      Cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  override def isWatching(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = isWatchingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def blocked[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = blockedUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(rd.restReads)
    }
  }

  override def block(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      Cache.set(isBlockingUrl(userId, otherId), true, cacheTime)
      Cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  override def unblock(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Unit] = {
    userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      Cache.set(isBlockingUrl(userId, otherId), false, cacheTime)
      Cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  override def isBlocking(userId: String, otherId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = isBlockingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def userAnnotations[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, EntityType.Annotation)
    userCall(url)
        .withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(rd.restReads)
    }
  }

  override def userLinks[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, EntityType.Link)
    userCall(url)
        .withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(rd.restReads)
    }
  }

  override def userBookmarks[A](userId: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rd: BackendReadable[A],  executionContext: ExecutionContext): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, EntityType.VirtualUnit)
    userCall(url).get().map { r =>
      parsePage(r, context = Some(url))(rd.restReads)
    }
  }
}
