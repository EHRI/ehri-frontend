package controllers.base

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth._
import play.api._
import play.api.mvc._

import scala.reflect.classTag


import play.api.Play.current


/*
 * Implementation of play2-auth
 * https://github.com/t2v/play20-auth/blob/master/README.md
 */

trait Authorizer extends Results with AuthConfig {

  /**
   * Dummy permission (which is not actually used.)
   */
  sealed trait Permission

  // Specific type of user-finder loaded via a plugin
  lazy val userFinder: models.sql.UserDAO = current.plugin(classOf[models.sql.UserDAO]).get
  
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
  type User = models.sql.User

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
  val idManifest = classTag[Id]

  /**
   * A duration of the session timeout in seconds
   */
  val sessionTimeoutInSeconds: Int = 604800 // 1 week

  /**
   * A function that returns a `User` object from an `Id`.
   * Describe the procedure according to your application.
   */
  def resolveUser(id: Id): Option[User] = userFinder.findByProfileId(id)

  /**
   * A redirect target after a successful user login.
   */
  def loginSucceeded(request: RequestHeader): PlainResult = {
    val uri = request.session.get("access_uri").getOrElse(controllers.routes.Search.search.url)
    request.session - "access_uri"
    Redirect(uri)
  }

  /**
   * A redirect target after a successful user logout.
   */
  def logoutSucceeded(request: RequestHeader): PlainResult = Redirect("/")

  /**
   * A redirect target after a failed authentication.
   */
  def authenticationFailed(request: RequestHeader): PlainResult = {
    if (ControllerHelpers.isAjax(request))
      Unauthorized("authentication failed")
    else
      Redirect(controllers.routes.Application.login).withSession("access_uri" -> request.uri)
  }

  /**
   * A redirect target after a failed authorization.
   */
  def authorizationFailed(request: RequestHeader): PlainResult = Forbidden("no permission")

  /**
   * A function that authorizes a user by `Authority`.
   * Describe the procedure according to your application.
   */
  def authorize(user: User, authority: Authority): Boolean = {
    // FIXME: Need to use ACL for this, but the play20-auth scheme might not fit perfectly
    // with ours because of the split between a User account (sql) and a UserProfile. For
    // the time being we do authorization ourselves and don't worry about implementing
    // this function properly.
    true
  }
}
