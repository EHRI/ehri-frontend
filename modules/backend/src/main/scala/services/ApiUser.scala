package services

/**
 * An abstract data type representing either an authenticated user with
 * a given string ID, or an anonymous user.
 */
object ApiUser {
  /**
   * Construct a new `ApiUser` from an `Option[String]` which is
   * present if the user is authenticated.
   *
   * @param opt an optional ID
   */
  def apply(opt: Option[String]): ApiUser = opt map AuthenticatedUser getOrElse AnonymousUser
}

sealed abstract class ApiUser extends Product with Serializable {
  /**
   * Returns true if the ApiUser is anonymous
   */
  def isAnonymous: Boolean

  /**
   * Returns true if the user is authenticated
   * @return
   */
  def isAuthenticated: Boolean = !isAnonymous

  /**
   * Convert the current api user into an `Option[String]`, with
   * the ID present if the user is authenticated.
   */
  def toOption: Option[String] = this match {
    case AuthenticatedUser(id) => Some(id)
    case AnonymousUser => None
  }
}

/**
 * A user that has been authenticated.
 *
 * @param id the user's ID
 */
case class AuthenticatedUser(id: String) extends ApiUser {
  def isAnonymous = false
}

/**
 * An anonymous user
 */
case object AnonymousUser extends ApiUser {
  def isAnonymous = true
}

