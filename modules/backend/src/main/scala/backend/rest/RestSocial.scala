package backend.rest

import backend._
import utils.caching.FutureCache
import scala.concurrent.Future
import utils.{Page, PageParams}
import defines.EntityType

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestSocial extends Social with RestDAO with RestContext {

  import backend.rest.Constants._

  private def requestUrl = s"$baseUrl/${EntityType.UserProfile}"

  private def followingUrl(userId: String) = enc(requestUrl, userId, "following")

  private def watchingUrl(userId: String) = enc(requestUrl, userId, "watching")

  private def blockedUrl(userId: String) = enc(requestUrl, userId, "blocked")

  private def isFollowingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isFollowing", otherId)

  private def isWatchingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isWatching", otherId)

  private def isBlockingUrl(userId: String, otherId: String) = enc(requestUrl, userId, "isBlocking", otherId)

  override def follow[U: Resource](userId: String, otherId: String): Future[Unit] = {
    userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      checkError(r)
      cache.set(isFollowingUrl(userId, otherId), true, cacheTime)
      cache.remove(followingUrl(userId))
      cache.remove(canonicalUrl(userId))
    }
  }

  override def unfollow[U: Resource](userId: String, otherId: String): Future[Unit] = {
    userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      checkError(r)
      cache.set(isFollowingUrl(userId, otherId), false, cacheTime)
      cache.remove(followingUrl(userId))
      cache.remove(canonicalUrl(userId))
    }
  }

  override def isFollowing(userId: String, otherId: String): Future[Boolean] = {
    val url = isFollowingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def isFollower(userId: String, otherId: String): Future[Boolean] = {
    userCall(enc(requestUrl, userId, "isFollower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  override def followers[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]] = {
    val url: String = enc(requestUrl, userId, "followers")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[U].restReads)
    }
  }

  override def following[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]] = {
    val url: String = followingUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[U].restReads)
    }
  }

  override def watching[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, "watching")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def watch(userId: String, otherId: String): Future[Unit] = {
    userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      cache.set(isWatchingUrl(userId, otherId), true, cacheTime)
      cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  override def unwatch(userId: String, otherId: String): Future[Unit] = {
    userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      cache.set(isWatchingUrl(userId, otherId), false, cacheTime)
      cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  override def isWatching(userId: String, otherId: String): Future[Boolean] = {
    val url = isWatchingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def blocked[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = blockedUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def block(userId: String, otherId: String): Future[Unit] = {
    userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      cache.set(isBlockingUrl(userId, otherId), true, cacheTime)
      cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  override def unblock(userId: String, otherId: String): Future[Unit] = {
    userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      cache.set(isBlockingUrl(userId, otherId), false, cacheTime)
      cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  override def isBlocking(userId: String, otherId: String): Future[Boolean] = {
    val url = isBlockingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def userAnnotations[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, EntityType.Annotation)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def userLinks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, EntityType.Link)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def userBookmarks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(requestUrl, userId, EntityType.VirtualUnit)
    userCall(url).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }
}
