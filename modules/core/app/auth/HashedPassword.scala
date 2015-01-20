package auth

import org.mindrot.jbcrypt.BCrypt

/**
 * Wrapper around a plain password string.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
object HashedPassword {
  def fromPlain(plain: String) = new HashedPassword(BCrypt.hashpw(plain.toString, BCrypt.gensalt()))
  def fromHashed(hashed: String) = new HashedPassword(hashed)
}

case class HashedPassword private(s: String) {
  def check(pw: String) =  BCrypt.checkpw(pw, s)

  override def toString = s
}
