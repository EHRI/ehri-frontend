package controllers.base

import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.mvc._

import scala.concurrent.duration.FiniteDuration


trait ControllerHelpers extends play.api.i18n.I18nSupport {

  private def logger = Logger(getClass)

  protected implicit def config: play.api.Configuration

  protected implicit def cache: SyncCacheApi

  /**
    * Session key for last page prior to login
    */
  protected val ACCESS_URI: String = "access_uri"

  /**
    * Fetch a value from config or throw an error.
    */
  protected def getConfigInt(key: String): Int =
    config.getInt(key).getOrElse(sys.error(s"Missing config key: $key"))

  /**
    * Fetch a value from config or throw an error.
    */
  protected def getConfigString(key: String): String =
    config.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  /**
    * Fetch a value from config or default to a fallback.
    */
  protected def getConfigString(key: String, fallback: String): String =
    config.getString(key).getOrElse(fallback)

  /**
    * Check if a request is Ajax.
    */
  protected def isAjax(implicit request: RequestHeader): Boolean = utils.http.isAjax

  /**
    * Check a particular remote address doesn't exceed a rate limit for a
    * given action. Whenever this function is called the user's
    */
  protected def checkRateLimit[A](limit: Int, duration: FiniteDuration)(implicit request: Request[A]): Boolean = {
    val ip = request.remoteAddress
    val key = request.path + ip
    val count = cache.get(key).getOrElse(0)
    logger.debug(s"Check rate limit: Limit $limit, timeout $duration, ip: $ip, key: $key, current: $count")
    if (count < limit) {
      cache.set(key, count + 1, duration)
      true
    } else {
      logger.warn(s"Rate limit refusal for IP $ip at ${request.path}")
      false
    }
  }
}