import models.Account
import models.sql.MockAccount
import play.api.Play.current

package object mocks {

  val privilegedUser = MockAccount("mike", "example1@example.com")
  val unprivilegedUser = MockAccount("reto", "example2@example.com")

  val userFixtures = collection.mutable.HashMap[String,Account] (
    privilegedUser.id -> privilegedUser,
    unprivilegedUser.id -> unprivilegedUser
  )
}