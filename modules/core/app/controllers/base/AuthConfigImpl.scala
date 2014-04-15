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

  def globalConfig: global.GlobalConfig

  // Specific type of user-finder loaded via a plugin
  def userDAO: models.AccountDAO

  def defaultLoginUrl: Call = globalConfig.routeRegistry.default
  def defaultLogoutUrl: Call = globalConfig.routeRegistry.default
  def defaultAuthFailedUrl: Call = globalConfig.routeRegistry.login

  protected val ACCESS_URI: String = "access_uri"


  /**
   * Dummy permission (which is not actually used.)
   */
  sealed trait Permission

  type Id = String

  override lazy val idContainer: IdContainer[Id] = new CookieIdContainer[Id]

  /**
   * Whether use the secure option or not use it in the cookie.
   * However default is false, I strongly recommend using true in a production.
   */
  override lazy val cookieSecureOption: Boolean = play.api.Play.current.configuration
      .getBoolean("auth.cookie.secure").getOrElse(false)

  /** 
   * A type that represents a user in your application.
   * `User`, `Account` and so on.
   */
  type User = models.Account

  /**
   * A type that is defined by every action for authorization.
   * This sample uses the following trait.
   *
   * sealed trait Permission
   * case object Administrator extends Permission
   * case object NormalUser extends Permission
   */
  type Authority = Permission

  /**
   * A `ClassManifest` is used to get an id from the Cache API.
   * Basically use the same setting as the following.
   */
  val idTag = classTag[Id]

  /**
   * A duration of the session timeout in seconds
   */
  val sessionTimeoutInSeconds: Int = 604800 // 1 week

  /**
   * A function that returns a `User` object from an `Id`.
   * Describe the procedure according to your application.
   */
  def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]] = immediate(userDAO.findByProfileId(id))

  /**
   * A redirect target after a successful user login.
   */
  def loginSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    val uri = request.session.get("access_uri").getOrElse(defaultLoginUrl.url)
    Logger.logger.debug("Redirecting logged-in user to: {}", uri)
    immediate(Redirect(uri).withSession(request.session - "access_uri"))
  }

  /**
   * A redirect target after a successful user logout.
   */
  def logoutSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]
        = immediate(Redirect(defaultLogoutUrl))

  /**
   * A redirect target after a failed authentication.
   */
  def authenticationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    if (utils.isAjax(request)) {
      Logger.logger.warn("Auth failed for: {}", request.toString())
      immediate(Unauthorized("authentication failed"))
    } else {
      immediate(Redirect(defaultAuthFailedUrl).withSession(ACCESS_URI -> request.uri))
    }
  }

  /**
   * A redirect target after a failed authorization.
   */
  def authorizationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]
      = immediate(Forbidden("no permission"))

  /**
   * A function that authorizes a user by `Authority`.
   * We don't use this because Authorization is done with our own ACL.
   */
  def authorize(user: User, authority: Authority)(implicit context: ExecutionContext): Future[Boolean]
      = immediate(true)
}
