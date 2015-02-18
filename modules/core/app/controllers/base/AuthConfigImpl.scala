package controllers.base

import jp.t2v.lab.play2.auth._
import play.api.mvc._

import scala.reflect.classTag
import scala.concurrent.{ExecutionContext,Future}
import scala.concurrent.Future.{successful => immediate}
import play.api.Logger

/*
 * Implementation of play2-auth
 * https://github.com/t2v/play20-auth/blob/master/README.md
 */

trait AuthConfigImpl extends AuthConfig with Results {

  import play.api.Play.current

  protected def globalConfig: global.GlobalConfig

  /**
   * The type of ID
   */
  type Id = String

  /**
   * A type that represents a user in your application.
   * `User`, `Account` and so on.
   */
  type User = models.Account

  // Specific type of user-finder loaded via a plugin
  protected def accounts: auth.AccountManager

  // Override these if necessary...
  def defaultLoginUrl: Call = Call("GET", "/login")
  def defaultLogoutUrl: Call = Call("GET", "/logout")
  def defaultAuthFailedUrl: Call = Call("GET", "/login")

  protected val ACCESS_URI: String = "access_uri"

  /**
   * The way user sessions are stored.
   */
  override lazy val idContainer: AsyncIdContainer[Id] = AsyncIdContainer(new CookieIdContainer[Id])

  /**
   * Whether use the secure option or not use it in the cookie.
   * However default is false, I strongly recommend using true in a production.
   */
  override lazy val cookieSecureOption: Boolean =
    current.configuration.getBoolean("auth.cookie.secure").getOrElse(false)

  /**
   * A duration of the session timeout in seconds
   */
  override lazy val sessionTimeoutInSeconds: Int =
    current.configuration.getInt("auth.session.timeout").getOrElse(60 * 60 * 24) // default 1 day

  /**
   * A `ClassManifest` is used to get an id from the Cache API.
   * Basically use the same setting as the following.
   */
  override val idTag = classTag[Id]

  /**
   * A function that returns a `User` object from an `Id`.
   * Describe the procedure according to your application.
   */
  def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] =
    accounts.findById(id)

  /**
   * A redirect target after a successful user login.
   */
  override def loginSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    val uri = request.session.get(ACCESS_URI).getOrElse(defaultLoginUrl.url)
    Logger.logger.debug("Redirecting logged-in user to: {}", uri)
    immediate(Redirect(uri).withSession(request.session - ACCESS_URI))
  }

  /**
   * A redirect target after a successful user logout.
   */
  override def logoutSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(Redirect(defaultLogoutUrl))

  /**
   * A redirect target after a failed authentication.
   */
  override def authenticationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(Unauthorized("not authenticated"))

  /**
   * A redirect target after a failed authorization.
   */
  override def authorizationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(Forbidden("no permission"))

  /**
   * A function that authorizes a user by `Authority`.
   * We don't use this because Authorization is done with our own ACL.
   */
  override def authorize(user: User, authority: Authority)(implicit context: ExecutionContext): Future[Boolean] =
    immediate(true)
}
