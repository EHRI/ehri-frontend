package controllers.base

import jp.t2v.lab.play20.auth._
import play.api._
import play.api.mvc._


import play.api.Play.current
import controllers.routes

/*
 * Implementation of play2-auth
 * https://github.com/t2v/play20-auth/blob/master/README.md
 */

sealed trait Permission
case object Administrator extends Permission
case object NormalUser extends Permission


trait Authorizer extends Results with AuthConfig {
  
  // Specific type of user-finder loaded via a plugin
  lazy val userFinder: models.sql.UserDAO = current.plugin(classOf[models.sql.UserDAO]).get
  
  type Id = String

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
  val idManifest: ClassManifest[Id] = classManifest[Id]

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
    val uri = request.session.get("access_uri").getOrElse("/") // FIXME: Redirect somewhere useful...
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
  def authenticationFailed(request: RequestHeader): PlainResult = 
    Redirect(controllers.routes.Application.login).withSession("access_uri" -> request.uri)

  /**
   * A redirect target after a failed authorization.
   */
  def authorizationFailed(request: RequestHeader): PlainResult = Forbidden("no permission")

  /**
   * A function that authorizes a user by `Authority`.
   * Describe the procedure according to your application.
   */
  def authorize(user: User, authority: Authority): Boolean = {
    // FIXME: Need to use ACL for this...
    true
  }
}
