package models.sql

import models.{Account,AccountDAO,HashedPassword}
import java.util.UUID

case class MockAccount(id: String, email: String) extends Account {
  def updatePassword(hashed: HashedPassword): Account = this
  def setPassword(data: HashedPassword): Account = this
  def delete(): Boolean = {
    mocks.userFixtures.remove(id)
    true
  }
  def createResetToken(token: UUID) = MockAccountDAO.tokens += token.toString -> id
  def expireTokens() = {  // Bit gross this, dealing with Mutable state...
    val indicesToDelete = for {
      (t, i) <- MockAccountDAO.tokens.zipWithIndex if t._2 == id
    } yield i
    for (i <- (MockAccountDAO.tokens.size -1) to 0 by -1) if (indicesToDelete contains i) MockAccountDAO.tokens remove i
  }
}

object MockAccountDAO {
  val tokens = collection.mutable.ListBuffer.empty[(String,String)]
}

/**
 * Find a user given their profile from the fixture store.
 * @param app
 */
class MockAccountDAO(app: play.api.Application) extends AccountDAO {

  def findByProfileId(id: String): Option[Account]
        = mocks.userFixtures.get(id)

  def findByEmail(email: String): Option[Account]
  = mocks.userFixtures.values.find(_.email == email)

  def create(id: String, email: String): Option[Account] = {
    val user = MockAccount(id, email)
    mocks.userFixtures.put(id, user)
    Some(user)
  }

  def findByResetToken(token: String): Option[Account] = MockAccountDAO.tokens.find(_._1 == token).flatMap { case (t, p) =>
    findByProfileId(p)
  }
}