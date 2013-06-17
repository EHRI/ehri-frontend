import models.sql.{OpenIDUser, User}
import play.api.Play.current

package object mocks {

  val MOCK_EMAIL = "example@example.com"

  // Profile ID must be passed in configuration
  def MOCK_USER = new models.sql.OpenIDUser(
      id=1L,
      email=MOCK_EMAIL,
      profile_id=current.configuration.getString("test.user.profile_id").getOrElse("anonymous"))

  object UserFixtures {
    // These depend on the Neo4j Server fixtures
    val privilegedUser = new OpenIDUser(1L, "example1@example.com", "mike")
    val unprivilegedUser = new OpenIDUser(1L, "example2@example.com", "reto")

    // This is mutable so we can add users to it dynamically
    val all = collection.mutable.ArrayBuffer[OpenIDUser](privilegedUser, unprivilegedUser)
  }
}