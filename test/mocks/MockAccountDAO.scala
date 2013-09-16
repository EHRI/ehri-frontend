package mocks

import models.{Account,AccountDAO}
import java.util.UUID

case class MockAccount(email: String, profile_id: String) extends Account {
  def updatePassword(hashed: String): Account = this
  def setPassword(data: String): Account = this
  def delete(): Boolean = {
    userFixtures.remove(profile_id)
    true
  }
}

/**
 * Find a user given their profile from the fixture store.
 * @param app
 */
class MockAccountDAO(app: play.api.Application) extends AccountDAO {

  val tokens = collection.mutable.ListBuffer.empty[(String,String)]

  def findByProfileId(profile_id: String): Option[Account]
        = mocks.userFixtures.get(profile_id)

  def findByEmail(email: String): Option[Account]
  = mocks.userFixtures.values.find(_.email == email)

  def create(email: String, profile_id: String): Option[Account] = {
    val user = MockAccount(email, profile_id)
    mocks.userFixtures.put(profile_id, user)
    Some(user)
  }

  def findByResetToken(token: String): Option[Account] = tokens.find(_._1 == token).flatMap { case (t, p) =>
    findByProfileId(p)
  }
  def createResetToken(token: UUID, profileId: String) = tokens += token.toString -> profileId
  def expireToken(token: String) = tokens.remove(tokens.indexWhere(_._1 == token))
}