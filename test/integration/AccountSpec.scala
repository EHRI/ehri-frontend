package integration

import auth.{HashedPassword, AccountManager}
import auth.sql.SqlAccountManager
import play.api.db.DB
import anorm._
import anorm.SqlParser._
import helpers.WithSqlFixtures
import models.Account
import play.api.test.{FakeApplication, PlaySpecification}
import org.h2.jdbc.JdbcSQLException


/**
 * Spec for testing individual data access components work as expected.
 */
class AccountSpec extends PlaySpecification {

  def userDAO: AccountManager = SqlAccountManager()

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
        await(userDAO.findById(mocks.privilegedUser.id)) must beSome
        await(userDAO.findByEmail(mocks.privilegedUser.email)) must beSome
      }
    }

    "allow deactivating accounts" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        await(userDAO.findById(mocks.privilegedUser.id)) must beSome.which { user =>
          user.active must beTrue
          await(userDAO.update(user.copy(active = false)))
          await(userDAO.findById(mocks.privilegedUser.id)) must beSome.which { inactive =>
            inactive.active must beFalse
          }
        }
      }
    }

    "allow setting and updating user's passwords" in new WithSqlFixtures(new FakeApplication) {
      val userOpt: Option[Account] = await(userDAO.findByEmail(mocks.privilegedUser.email))
      userOpt must beSome.which { user =>
        val hashedPw = HashedPassword.fromPlain("foobar")
        await(userDAO.update(user.copy(password = Some(hashedPw))))
        await(userDAO.authenticate(mocks.privilegedUser.email, "foobar")) must beSome

        val hashedPw2 = HashedPassword.fromPlain("barfoo")
        await(userDAO.update(user.copy(password = Some(hashedPw2))))
        await(userDAO.authenticate(mocks.privilegedUser.email, "barfoo")) must beSome
      }
    }
  }


  "openid assoc" should {
    "find accounts by openid_url and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assocOpt = await(userDAO.openid.findByUrl(mocks.privilegedUser.id + "-openid-test-url"))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mocks.privilegedUser.email)

          val assoc2 = await(userDAO.openid.addAssociation(user, "another-test-url"))
          assoc2.user must beSome.which { u =>
            u must equalTo(user)
          }
        }
      }
    }
  }

  "oauth2 assoc" should {
    "find accounts by oauth2 provider info and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assocOpt = await(userDAO.oauth2.findByProviderInfo(mocks.privilegedUser.id + "1234", "google"))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mocks.privilegedUser.email)

          val assoc2 = await(userDAO.oauth2.addAssociation(user, "4321", "facebook"))
          assoc2.user must beSome.which { u =>
            u must equalTo(user)
          }
        }
      }
    }
  }
}
