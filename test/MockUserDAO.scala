




import models.sql.OpenIDUser

class MockUserDAO(app: play.api.Application) extends models.sql.UserDAO {
	def findByProfileId(profile_id: String) = Some(new OpenIDUser(-1L, "example@example.com", "mike"))
}