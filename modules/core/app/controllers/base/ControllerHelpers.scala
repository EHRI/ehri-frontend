package controllers.base

import java.util.concurrent.TimeUnit

import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc._
import scala.concurrent.duration.{FiniteDuration, Duration}


trait ControllerHelpers extends play.api.i18n.I18nSupport {

  implicit def config: play.api.Configuration
  implicit def cache: play.api.cache.CacheApi

  /**
   * Get the remote IP of a user, taking into account intermediate
   * proxying. FIXME: there's got to be a better way to do this.
   */
  protected def remoteIp(implicit request: RequestHeader): String =
    request.headers.get(HeaderNames.X_FORWARDED_FOR)
      .flatMap(_.split(",").map(_.trim).headOption)
      .getOrElse(request.remoteAddress)

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
  protected def checkRateLimit[A](implicit request: Request[A]): Boolean = {
    val limit: Int = getConfigInt("ehri.ratelimit.limit")
    val timeoutSecs: Int = getConfigInt("ehri.ratelimit.timeout")
    val ip = remoteIp(request)
    val key = request.path + ip
    val duration: FiniteDuration = Duration(timeoutSecs, TimeUnit.SECONDS)
    val count = cache.get(key).getOrElse(0)
    Logger.debug(s"Check rate limit: Limit $limit, timeout $duration, ip: $ip, key: $key, current: $count")
    if (count < limit) {
      cache.set(key, count + 1, duration)
      true
    } else {
      Logger.warn(s"Rate limit refusal for IP $ip at ${request.path}")
      false
    }
  }
}