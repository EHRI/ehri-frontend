package mocks

import models.sql.OpenIDUser

/**
 * Find a user given their profile from the fixture store.
 * @param app
 */
class MockUserDAO(app: play.api.Application) extends models.sql.UserDAO {
  def findByProfileId(profile_id: String): Option[OpenIDUser]
        = mocks.UserFixtures.all.find(_.profile_id == profile_id)
}