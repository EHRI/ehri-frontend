package services.data.caching

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import play.api.cache.SyncCacheApi

/**
 * Wrapper around Play's simple Cache api for more convenience when
 * using an asynchronous data source.
 */
object FutureCache {
  def getOrElse[A](key: String, expiration: Duration = Duration.Inf)(orElse : => Future[A])(implicit cache: SyncCacheApi, ct: ClassTag[A], executionContext: ExecutionContext): Future[A] = {
    cache.get[A](key) match {
      case Some(a) => Future.successful(a)
      case _ =>
        val value = orElse
        value.map { a =>
          cache.set(key, a, expiration)
          a
        }
    }
  }

  def set[A](key: String, expiration: Duration = Duration.Inf)(get: => Future[A])(implicit cache: SyncCacheApi, executionContext: ExecutionContext): Future[A] = {
    val value = get
    value.map { a =>
      cache.set(key, a, expiration)
      a
    }
  }
}
