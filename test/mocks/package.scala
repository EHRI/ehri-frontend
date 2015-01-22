import auth.HashedPassword
import models.{OpenIDAssociation, OAuth2Association, Account}

package object mocks {

  val privilegedUser = Account("mike", "example1@example.com", verified = true, staff = true)
  val unprivilegedUser = Account("reto", "example2@example.com", verified = true, staff = true)
  val moderator = Account("linda", "example3@example.com", verified = true, staff = true)
  val publicUser = Account("joeblogs", "example@aol.com", verified = true, staff = false)
  val unverifiedUser = Account("bobjohn", "example@yahoo.com", verified = false, staff = false)

  val googleOAuthAssoc = OAuth2Association("mike", "123456789", "google", Some(privilegedUser))
  val facebookOAuthAssoc = OAuth2Association("reto", "123456789", "facebook", Some(unprivilegedUser))
  val yahooOpenId = OpenIDAssociation("linda", "https://yahoo.com/openid", Some(moderator))

  // Users...
  val users = Map(
    privilegedUser.id -> privilegedUser,
    unprivilegedUser.id -> unprivilegedUser,
    moderator.id -> moderator,
    publicUser.id -> publicUser,
    unverifiedUser.id -> unverifiedUser
  )

  val oAuth2Associations = List(googleOAuthAssoc, facebookOAuthAssoc)

  val openIDAssociations = List(yahooOpenId)


  // Mutable vars that server as mock database tables
  var accountFixtures = users

  val tokenFixtures: collection.mutable.ListBuffer[(String,String,Boolean)] =
    collection.mutable.ListBuffer.empty[(String,String,Boolean)]

  val oauth2AssociationFixtures: collection.mutable.ListBuffer[OAuth2Association] =
    collection.mutable.ListBuffer.empty[OAuth2Association]

  val openIdAssociationFixtures: collection.mutable.ListBuffer[OpenIDAssociation] =
    collection.mutable.ListBuffer.empty[OpenIDAssociation]
}