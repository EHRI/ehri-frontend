package integration

import play.api.db.DB
import anorm._
import anorm.SqlParser._
import helpers.WithSqlFixtures
import models.{Account, AccountDAO}
import models.sql.{SqlAccount, OAuth2Association, OpenIDAssociation}
import play.api.test.{FakeApplication, PlaySpecification}
import org.h2.jdbc.JdbcSQLException


/**
 * Spec for testing individual data access components work as expected.
 */
class AccountSpec extends PlaySpecification {

  def userDAO: AccountDAO = SqlAccount

  "account db" should {
    "load fixtures with the right number of accounts" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        SQL("select count(*) from users").as(scalar[Long].single) must equalTo(5L)
      }
    }

    "enforce email uniqueness" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        SQL(
          """insert into users (id,email,verified,staff) values ('blah',{email},1,1)""")
          .on('email -> mocks.privilegedUser.email)
          .executeInsert() must throwA[JdbcSQLException]
      }
    }

    "find accounts by id and email" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        userDAO.findByProfileId(mocks.privilegedUser.id) must beSome
        userDAO.findByEmail(mocks.privilegedUser.email) must beSome
      }
    }

    "allow deactivating accounts" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        userDAO.findByProfileId(mocks.privilegedUser.id) must beSome.which { user =>
          user.active must beTrue
          user.setActive(active = false)
          userDAO.findByProfileId(mocks.privilegedUser.id) must beSome.which { inactive =>
            inactive.active must beFalse
          }
        }
      }
    }

    "allow setting and updating user's passwords" in new WithSqlFixtures(new FakeApplication) {
      val userOpt: Option[Account] = userDAO.findByEmail(mocks.privilegedUser.email)
      userOpt must beSome.which { user =>
        val hashedPw = userDAO.hashPassword("foobar")
        user.setPassword(hashedPw)
        userDAO.authenticate(mocks.privilegedUser.email, "foobar") must beSome

        val hashedPw2 = userDAO.hashPassword("barfoo")
        user.setPassword(hashedPw2)
        userDAO.authenticate(mocks.privilegedUser.email, "barfoo") must beSome
      }
    }
  }


  "openid assoc" should {
    "find accounts by openid_url and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assoc = OpenIDAssociation.findByUrl(mocks.privilegedUser.id + "-openid-test-url")
      assoc must beSome
      assoc.get.user must beSome
      assoc.get.user.get.email must beEqualTo(mocks.privilegedUser.email)

      val user = assoc.get.user.get

      OpenIDAssociation.addAssociation(user, "another-test-url")
      val assoc2 = OpenIDAssociation.findByUrl("another-test-url")
      assoc2 must beSome
      assoc2.get.user must beSome
      assoc2.get.user.get must equalTo(user)
    }
  }

  "oauth2 assoc" should {
    "find accounts by oauth2 provider info and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assoc = OAuth2Association.findByProviderInfo(mocks.privilegedUser.id + "1234", "google")
      println("ASSOC: " + assoc)
      assoc must beSome
      assoc.get.user must beSome
      assoc.get.user.get.email must beEqualTo(mocks.privilegedUser.email)

      val user = assoc.get.user.get

      OAuth2Association.addAssociation(user, "4321", "facebook")
      val assoc2 = OAuth2Association.findByProviderInfo("4321", "facebook")
      assoc2 must beSome
      assoc2.get.user must beSome
      assoc2.get.user.get must equalTo(user)
    }
  }
}
