import models.Account
import models.sql.MockAccount
import play.api.Play.current

package object mocks {

  val privilegedUser = MockAccount("mike", "example1@example.com", staff = true)
  val unprivilegedUser = MockAccount("reto", "example2@example.com", staff = true)
  val publicUser = MockAccount("joeblogs", "example@aol.com", staff = false)

  // Users...
  val users = Map(
    privilegedUser.id -> privilegedUser,
    unprivilegedUser.id -> unprivilegedUser,
    publicUser.id -> publicUser
  )

  // Mutable map that serves as a mock db...
  val userFixtures = collection.mutable.HashMap[String,Account](users.toSeq: _*)
}