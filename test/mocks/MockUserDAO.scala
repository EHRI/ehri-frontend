package mocks

import models.sql.OpenIDUser

class MockUserDAO(app: play.api.Application) extends models.sql.UserDAO {
  def findByProfileId(profile_id: String) = Some(MOCK_USER)
}