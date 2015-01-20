import models.Account

package object mocks {

  val privilegedUser = Account("mike", "example1@example.com", verified = true, staff = true, active = true, allowMessaging = true)
  val unprivilegedUser = Account("reto", "example2@example.com", verified = true, staff = true, active = true, allowMessaging = true)
  val moderator = Account("linda", "example3@example.com", verified = true, staff = true, active = true, allowMessaging = true)
  val publicUser = Account("joeblogs", "example@aol.com", verified = true, staff = false, active = true, allowMessaging = true)
  val unverifiedUser = Account("bobjohn", "example@yahoo.com", verified = false, staff = false, active = true, allowMessaging = true)

  // Users...
  val users = Map(
    privilegedUser.id -> privilegedUser,
    unprivilegedUser.id -> unprivilegedUser,
    moderator.id -> moderator,
    publicUser.id -> publicUser,
    unverifiedUser.id -> unverifiedUser
  )

  // Mutable map that serves as a mock db...
  var userFixtures = users
  val tokens = collection.mutable.ListBuffer.empty[(String,String,Boolean)]
}