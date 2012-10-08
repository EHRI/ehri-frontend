package controllers

import jp.t2v.lab.play20.auth._
import play.api._
import play.api.mvc._
import models.User

/*
 * Implementation of play2-auth
 * https://github.com/t2v/play20-auth/blob/master/README.md
 */

sealed trait Permission
case object Administrator extends Permission
case object NormalUser extends Permission

trait Authorizer extends Results with AuthConfig {
  type Id = String

  /** 
   * A type that represents a user in your application.
   * `User`, `Account` and so on.
   */
  type User = models.User

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
  def resolveUser(id: Id): Option[User] = models.User.findByIdSync(id)

  /**
   * A redirect target after a successful user login.
   */
  def loginSucceeded[A](request: Request[A]): PlainResult = {
    val uri = request.session.get("access_uri").getOrElse("/") // FIXME: Redirect somewhere useful...
    request.session - "access_uri"
    Redirect(uri)
  }

  /**
   * A redirect target after a successful user logout.
   */
  def logoutSucceeded[A](request: Request[A]): PlainResult = Redirect(routes.Application.login)

  /**
   * A redirect target after a failed authentication.
   */
  def authenticationFailed[A](request: Request[A]): PlainResult = 
    Redirect(routes.Application.login).withSession("access_uri" -> request.uri)

  /**
   * A redirect target after a failed authorization.
   */
  def authorizationFailed[A](request: Request[A]): PlainResult = Forbidden("no permission")

  /**
   * A function that authorizes a user by `Authority`.
   * Describe the procedure according to your application.
   */
  def authorize(user: User, authority: Authority): Boolean = {
    // FIXME: This is obvs. temporary
    println("Authorising: %s with %s".format(user, authority))
    (user.id, authority) match {
      case ("mikesname", _) => true
      case ("Mikeyb", _) => true
      case (_, NormalUser) => true
      case _ => false
    }
  }
}
