package auth.handler

import scala.util.Try

/**
  * The value of a login session cookie.
  *
  * @param userId   the account ID
  * @param version  the account's session version when the session started,
  *                 which ends the session when the account's version changes
  * @param issuedAt when the session started, in epoch seconds
  * @param lastSeen when the session was last used, in epoch seconds
  */
case class SessionToken(userId: String, version: Int, issuedAt: Long, lastSeen: Long) {
  def encode: String = s"$userId:$version:$issuedAt:$lastSeen"
}

object SessionToken {
  def parse(value: String): Option[SessionToken] = {
    // Parse from the right, in case the user ID contains a colon
    val parts = value.split(":", -1)
    if (parts.length < 4) None else Try {
      val Array(version, issuedAt, lastSeen) = parts.takeRight(3)
      SessionToken(parts.dropRight(3).mkString(":"), version.toInt, issuedAt.toLong, lastSeen.toLong)
    }.toOption.filter(_.userId.nonEmpty)
  }
}
