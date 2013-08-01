package mocks

import models.sql.{User, OpenIDUser}

case class MockUser(email: String, profile_id: String) extends User {
  def updatePassword(hashed: String): User = this
  def setPassword(data: String): User = this
  def delete(): Boolean = {
    userFixtures.remove(profile_id)
    true
  }
}

/**
 * Find a user given their profile from the fixture store.
 * @param app
 */
class MockUserDAO(app: play.api.Application) extends models.sql.UserDAO {
  def findByProfileId(profile_id: String): Option[User]
        = mocks.userFixtures.get(profile_id)

  def findByEmail(email: String): Option[User]
  = mocks.userFixtures.values.find(_.email == email)

  def create(email: String, profile_id: String): Option[User] = {
    val user = MockUser(email, profile_id)
    mocks.userFixtures.put(profile_id, user)
    Some(user)
  }
}