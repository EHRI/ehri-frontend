package models

import java.util.UUID

case class MockAccount(id: String, email: String, verified: Boolean = false, staff: Boolean = false, active: Boolean = true,
                        allowMessaging: Boolean = true) extends Account {
  def updatePassword(hashed: HashedPassword): Account = this
  def setPassword(data: HashedPassword): Account = this
  def setVerified(): Account = updateWith(this.copy(verified = true))
  def setActive(active: Boolean) = updateWith(this.copy(active = active))
  def setStaff(staff: Boolean) = updateWith(this.copy(staff = staff))
  def setAllowMessaging(allowMessaging: Boolean) = updateWith(this.copy(allowMessaging = allowMessaging))
  def verify(token: String): Account = updateWith(this.copy(verified = true))
  def delete(): Boolean = {
    mocks.userFixtures -= id
    true
  }
  def createResetToken(token: UUID) = MockAccountDAO.tokens += ((token.toString, id, false))
  def createValidationToken(token: UUID) = MockAccountDAO.tokens += ((token.toString, id, true))
  def expireTokens() = {  // Bit gross this, dealing with Mutable state...
    val indicesToDelete = for {
      (t, i) <- MockAccountDAO.tokens.zipWithIndex if t._2 == id
    } yield i
    for (i <- (MockAccountDAO.tokens.size -1) to 0 by -1) if (indicesToDelete contains i) MockAccountDAO.tokens remove i
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
  val tokens = collection.mutable.ListBuffer.empty[(String,String,Boolean)]

  // Mock authentication
  override def authenticate(email: String, pw: String, verified: Boolean = true)
      = mocks.userFixtures.find(u => u._2.email == email && u._2.verified == verified).map(_._2)

  def findVerifiedByProfileId(id: String, verified: Boolean = true): Option[Account]
      = mocks.userFixtures.get(id).filter(p => p.verified == verified)

  def findByProfileId(id: String): Option[Account]
      = mocks.userFixtures.get(id)

  def findVerifiedByEmail(email: String, verified: Boolean = true): Option[Account]
      = mocks.userFixtures.values.find(u => u.email == email && u.verified == verified)

  def findByEmail(email: String): Option[Account]
      = mocks.userFixtures.values.find(u => u.email == email)

  def create(id: String, email: String, verified: Boolean = false, staff: Boolean = false,
             allowMessaging: Boolean = true): Account = {
    val user = MockAccount(id, email, staff)
    mocks.userFixtures += id -> user
    user
  }

  def createWithPassword(id: String, email: String, verified: Boolean = false, staff: Boolean = false,
                         allowMessaging: Boolean = true, hashed: HashedPassword): Account = {
    create(id, email, verified, staff)
  }

  def findByResetToken(token: String, isSignUp: Boolean = false): Option[Account]
      = MockAccountDAO.tokens.find(t => t._1 == token && t._3 == isSignUp).flatMap { case (t, p, s) =>
    findByProfileId(p)
  }
}