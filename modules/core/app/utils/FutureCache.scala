package utils

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import play.api.cache.Cache

/**
 * Wrapper around Play's simple Cache api for more convenience when
 * using an asynchronous data source.
 */
object FutureCache {
  def getOrElse[A](key: String, expiration: Int = 0)(orElse : => Future[A])(implicit app: play.api.Application, ct: ClassTag[A], executionContext: ExecutionContext): Future[A] = {
    Cache.getAs[A](key) match {
      case Some(a) => Future.successful(a)
      case _ => {
        val value = orElse
        value.map { a =>
          Cache.set(key, a, expiration)
          a
        }
      }
    }
  }

  def set[A](key: String, expiration: Int = 0)(get: => Future[A])(implicit app: play.api.Application, ct: ClassTag[A], executionContext: ExecutionContext): Future[A] = {
    val value = get
    value.map { a =>
      Cache.set(key, a, expiration)
      a
    }
  }
}