package models

import auth.HashedPassword
import play.api.libs.json.{Json, JsValue, Writes}
import play.api.Plugin
import java.util.UUID
import org.mindrot.jbcrypt.BCrypt
import utils.PageParams
import jp.t2v.lab.play2.auth._



/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Account {
	def email: String
	def id: String
  val verified: Boolean
  val staff: Boolean
  val active: Boolean
  def password: Option[HashedPassword] = None
  def setPassword(hashed: HashedPassword): Account
  def setVerified(): Account
  def setActive(active: Boolean): Account
  def setStaff(staff: Boolean): Account
  def setAllowMessaging(allowMessaging: Boolean): Account
  def verify(token: String): Account
  def delete(): Boolean
  def allowMessaging: Boolean
  def createResetToken(uuid: UUID): Unit
  def createValidationToken(uuid: UUID): Unit
  def expireTokens(): Unit
  def update(): Unit
}

trait AccountDAO extends Plugin {
  def checkPassword(p: String, h: HashedPassword) = BCrypt.checkpw(p, h.toString)
  def hashPassword(p: String): HashedPassword = HashedPassword.fromPlain(p)

  def authenticate(email: String, pw: String, verifiedOnly: Boolean = false): Option[Account] = {
    for {
      acc <- findByEmail(email)
      hashed <- acc.password if checkPassword(pw, hashed) && (if(verifiedOnly) acc.verified else true)
    } yield acc
  }
  def findVerifiedByProfileId(id: String, verified: Boolean = true): Option[Account]
	def findByProfileId(id: String): Option[Account]
  def findVerifiedByEmail(email: String, verified: Boolean = true): Option[Account]
  def findAll(params: PageParams = PageParams.empty): Seq[Account]
  def findByEmail(email: String): Option[Account]
  def create(id: String, email: String, verified: Boolean, staff: Boolean, allowMessaging: Boolean): Account
  def createWithPassword(id: String, email: String, verified: Boolean, staff: Boolean, allowMessaging: Boolean, hashed: HashedPassword): Account
  def findByResetToken(token: String, isSignUp: Boolean = false): Option[Account]

  def storeLoginToken(token: AuthenticityToken, userId: String, timeoutInSeconds: Int): Unit
  def removeLoginToken(token: AuthenticityToken): Unit
  def removeLoginTokens(userId: String): Unit
  def getByLoginToken(token: AuthenticityToken): Option[String]
}