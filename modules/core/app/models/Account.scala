package models

import play.api.libs.json.{Json, JsValue, Writes}
import play.api.Plugin
import java.util.UUID
import org.mindrot.jbcrypt.BCrypt


private [models] object HashedPassword {
  def fromPlain(plain: String) = new HashedPassword(BCrypt.hashpw(plain.toString, BCrypt.gensalt()))
  def fromHashed(hashed: String) = new HashedPassword(hashed)
}

private[models] case class HashedPassword private(s: String) {
  override def toString = s
}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Account {
	def email: String
	def id: String
  val verified: Boolean
  val staff: Boolean
  def password: Option[HashedPassword] = None
  def setPassword(hashed: HashedPassword): Account
  def verify(token: String): Account
  def delete(): Boolean
  def createResetToken(uuid: UUID): Unit
  def createValidationToken(uuid: UUID): Unit
  def expireTokens(): Unit
}

object Account {
  implicit val userWrites: Writes[Account] = new Writes[Account] {
    def writes(user: Account): JsValue = Json.obj(
      "email" -> user.email,
      "profile_id" -> user.id
    )
  }

  def checkPassword(p: String, h: HashedPassword) = BCrypt.checkpw(p, h.toString)
  def hashPassword(p: String): HashedPassword = HashedPassword.fromPlain(p)
}

trait AccountDAO extends Plugin {
  def authenticate(email: String, pw: String, verified: Boolean = true): Option[Account] = for {
      acc <- findByEmail(email)
      hashed <- acc.password if Account.checkPassword(pw, hashed) && acc.verified
  } yield acc
	def findByProfileId(id: String, verified: Boolean = true): Option[Account]
  def findByEmail(email: String, verified: Boolean = true): Option[Account]
  def create(id: String, email: String, verified: Boolean, staff: Boolean): Account
  def createWithPassword(id: String, email: String, verified: Boolean, staff: Boolean, hashed: HashedPassword): Account
  def findByResetToken(token: String, isSignUp: Boolean = false): Option[Account]
}