package auth.handler.cookie

import javax.inject.Inject
import auth.handler.TokenAccessor
import views.AppConfig
import play.api.Configuration
import play.api.libs.crypto.CookieSigner
import play.api.mvc.Cookie.SameSite
import play.api.mvc.{Cookie, DiscardingCookie, RequestHeader, Result}

/**
  * Authentication token accessor.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
case class CookieTokenAccessor @Inject()()(val signer: CookieSigner,
                                           config: Configuration, conf: AppConfig) extends TokenAccessor {

  val cookieName: String = config.get[String]("auth.session.cookieName")
  val cookieSecureOption: Boolean = conf.https
  val cookieHttpOnlyOption: Boolean = true
  val cookieDomainOption: Option[String] = None
  val cookiePathOption: String = "/"
  val cookieMaxAge: Option[Int] = None
  val cookieSameSite: SameSite = config.getOptional[String]("auth.session.cookieSameSite") match {
    case Some("lax") => SameSite.Lax
    case Some("strict") => SameSite.Strict
    case _ => SameSite.None
  }

  override def put(token: String)(result: Result)(implicit request: RequestHeader): Result = {
    val c = Cookie(cookieName, sign(token), cookieMaxAge, cookiePathOption,
      cookieDomainOption, cookieSecureOption, cookieHttpOnlyOption, Some(cookieSameSite))
    result.withCookies(c)
  }

  override def extract(request: RequestHeader): Option[String] = {
    request.cookies.get(cookieName).flatMap(c => verifyHmac(c.value))
  }

  override def delete(result: Result)(implicit request: RequestHeader): Result = {
    result.discardingCookies(DiscardingCookie(cookieName))
  }
}
