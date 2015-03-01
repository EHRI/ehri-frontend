package controllers.base

import play.api.Logger
import play.api.cache.Cache
import play.api.http.HeaderNames
import play.api.mvc._

trait ControllerHelpers {

  import play.api.Play.current

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
  protected def getConfigInt(key: String)(implicit app: play.api.Application): Int =
    app.configuration.getInt(key).getOrElse(sys.error(s"Missing config key: $key"))

  /**
   * Fetch a value from config or throw an error.
   */
  protected def getConfigString(key: String)(implicit app: play.api.Application): String =
    app.configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  /**
   * Fetch a value from config or default to a fallback.
   */
  protected def getConfigString(key: String, fallback: String)(implicit app: play.api.Application): String =
    app.configuration.getString(key).getOrElse(fallback)

  /**
   * Check if a request is Ajax.
   */
  protected def isAjax(implicit request: RequestHeader): Boolean = utils.isAjax

  /**
   * Check a particular remote address doesn't exceed a rate limit for a
   * given action. Whenever this function is called the user's
   */
  protected def checkRateLimit[A](implicit request: Request[A]): Boolean = {
    val limit: Int = getConfigInt("ehri.ratelimit.limit")
    val timeoutSecs: Int = getConfigInt("ehri.ratelimit.timeout")
    val ip = remoteIp(request)
    val key = request.path + ip
    val count = Cache.getOrElse(key, timeoutSecs)(0)
    if (count < limit) {
      Cache.set(key, count + 1, timeoutSecs)
      true
    } else {
      Logger.warn(s"Rate limit refusal for IP $ip at ${request.path}")
      false
    }
  }
}