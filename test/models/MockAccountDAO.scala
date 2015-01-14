package models

import java.util.UUID
import auth.HashedPassword
import utils.PageParams
import play.api.cache.Cache
import jp.t2v.lab.play2.auth.AuthenticityToken
import play.api.Play.current

case class MockAccount(id: String, email: String, verified: Boolean = false, staff: Boolean = false, active: Boolean = true,
                        allowMessaging: Boolean = true, override val password: Option[HashedPassword] = None) extends Account {
  def updatePassword(hashed: HashedPassword): Account = updateWith(this.copy(password = Some(hashed)))
  def setPassword(data: HashedPassword): Account = updateWith(this.copy(password = Some(data)))
  def setVerified(): Account = updateWith(this.copy(verified = true))
  def setActive(active: Boolean) = updateWith(this.copy(active = active))
  def setStaff(staff: Boolean) = updateWith(this.copy(staff = staff))
  def setAllowMessaging(allowMessaging: Boolean) = updateWith(this.copy(allowMessaging = allowMessaging))
  def verify(token: String): Account = updateWith(this.copy(verified = true))
  def delete(): Boolean = {
    mocks.userFixtures -= id
    true
  }
  def createResetToken(token: UUID) = mocks.tokens += ((token.toString, id, false))
  def createValidationToken(token: UUID) = mocks.tokens += ((token.toString, id, true))
  def expireTokens() = {  // Bit gross this, dealing with Mutable state...
    val indicesToDelete = for {
      (t, i) <- mocks.tokens.zipWithIndex if t._2 == id
    } yield i
    for (i <- (mocks.tokens.size -1) to 0 by -1) if (indicesToDelete contains i) mocks.tokens remove i
  }

  private def updateWith(acc: MockAccount): MockAccount = {
    mocks.userFixtures += acc.id -> acc
    acc
  }

  def update(): Unit = mocks.userFixtures += id -> this
}

/**
 * Find a user given their profile from the fixture store.
 */
object MockAccountDAO extends AccountDAO {

  def findVerifiedByProfileId(id: String, verified: Boolean = true): Option[Account] =
    mocks.userFixtures.get(id).filter(p => p.verified == verified)

  def findByProfileId(id: String): Option[Account] =
    mocks.userFixtures.get(id)

  def findVerifiedByEmail(email: String, verified: Boolean = true): Option[Account] =
    mocks.userFixtures.values.find(u => u.email == email && u.verified == verified)

  def findByEmail(email: String): Option[Account] =
    mocks.userFixtures.values.find(u => u.email == email)

  def create(id: String, email: String, verified: Boolean = false, staff: Boolean = false,
             allowMessaging: Boolean = true): Account = {
    val user = MockAccount(id, email, staff, allowMessaging)
    mocks.userFixtures += id -> user
    user
  }

  def createWithPassword(id: String, email: String, verified: Boolean = false, staff: Boolean = false,
                         allowMessaging: Boolean = true, hashed: HashedPassword): Account = {
    create(id, email, verified, staff, allowMessaging)
      .setPassword(hashed)
  }

  def findByResetToken(token: String, isSignUp: Boolean = false): Option[Account] =
    mocks.tokens.find(t => t._1 == token && t._3 == isSignUp).flatMap { case (t, p, s) =>
    findByProfileId(p)
  }

  def findAll(params: PageParams): Seq[Account] = mocks.userFixtures.values.toSeq

  // Auth tokens
  private val tokenSuffix = ":token"
  private val userIdSuffix = ":userId"

  private def unsetToken(token: AuthenticityToken) {
    Cache.remove(token + tokenSuffix)
  }
  private def unsetUserId(userId: String) {
    Cache.remove(userId.toString + userIdSuffix)
  }

  def storeLoginToken(token: AuthenticityToken, userId: String, timeoutInSeconds: Int): Unit = {
    Cache.set(token + tokenSuffix, userId, timeoutInSeconds)
    Cache.set(userId.toString + userIdSuffix, token, timeoutInSeconds)
  }

  def removeLoginToken(token: AuthenticityToken): Unit = {
    getByLoginToken(token).foreach(unsetUserId)
    unsetToken(token)
  }

  def getByLoginToken(token: AuthenticityToken): Option[String] = {
    Cache.getAs[String](token + tokenSuffix)
  }

  def removeLoginTokens(userId: String): Unit = {
    Cache.getAs[String](userId.toString + userIdSuffix) foreach unsetToken
    unsetUserId(userId)
  }
}