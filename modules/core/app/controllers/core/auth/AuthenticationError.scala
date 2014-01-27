package controllers.core.auth

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class AuthenticationError(msg: String) extends Exception(msg)

object AuthenticationError {
  def apply(msg: String, cause: Throwable) = new AuthenticationError(msg).initCause(cause)
}
