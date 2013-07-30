import models.sql.User
import play.api.Play.current

package object mocks {

  val MOCK_EMAIL = "example@example.com"

  // Profile ID must be passed in configuration
  def MOCK_USER = MockUser(
      email=MOCK_EMAIL,
      profile_id=current.configuration.getString("test.user.profile_id").getOrElse("anonymous"))

  val privilegedUser = MockUser("example1@example.com", "mike")
  val unprivilegedUser = MockUser("example2@example.com", "reto")

  val userFixtures = collection.mutable.HashMap[String,User] (
    privilegedUser.profile_id -> privilegedUser,
    unprivilegedUser.profile_id -> unprivilegedUser
  )
}