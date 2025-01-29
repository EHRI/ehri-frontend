package auth

case class AuthenticationError(msg: String, detailKey: Option[String] = None) extends Exception(msg)

object AuthenticationError {
  def apply(msg: String): AuthenticationError = apply(msg, Option.empty, null)

  def apply(msg: String, cause: Throwable): AuthenticationError = apply(msg, Option.empty, cause)

  def apply(msg: String, detailKey: Option[String], cause: Throwable): AuthenticationError = {
    val e = new AuthenticationError(msg, detailKey)
    e.initCause(cause)
    e
  }
}
