package integration

import java.util.UUID

import auth.{HashedPassword, AccountManager}
import auth.sql.SqlAccountManager
import play.api.db.DB
import anorm._
import anorm.SqlParser._
import helpers.WithSqlFixtures
import models.Account
import play.api.test.{FakeApplication, PlaySpecification}
import org.h2.jdbc.JdbcSQLException
import utils.PageParams


/**
 * Spec for testing individual data access components work as expected.
 */
class AccountSpec extends PlaySpecification {

  def userDAO: AccountManager = SqlAccountManager()(play.api.Play.current)

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

    "find multiple accounts by id" in new WithSqlFixtures(new FakeApplication) {
      val accounts: Seq[Account] = await(userDAO.findAllById(
        Seq(mocks.privilegedUser.id, mocks.unprivilegedUser.id)))
      accounts.find(_.id == mocks.privilegedUser.id) must beSome
      accounts.find(_.id == mocks.unprivilegedUser.id) must beSome
    }

    "find accounts by token" in new WithSqlFixtures(new FakeApplication) {
      val uuid = UUID.randomUUID()
      await(userDAO.createToken(mocks.privilegedUser.id, uuid, isSignUp = true))
      await(userDAO.findByToken(uuid.toString, isSignUp = true)) must beSome.which { acc =>
        acc.id must equalTo(mocks.privilegedUser.id)
      }
    }

    "find all accounts with pagination" in new WithSqlFixtures(new FakeApplication) {
      val accounts: Seq[Account] = await(userDAO.findAll())
      accounts.size must equalTo(5)
      val pagedAccounts: Seq[Account] = await(userDAO.findAll(PageParams(limit = 2)))
      pagedAccounts.size must equalTo(2)
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
        await(userDAO.update(user.copy(password = Some(HashedPassword.fromPlain("foobar")))))
        await(userDAO.authenticateByEmail(mocks.privilegedUser.email, "foobar")) must beSome

        await(userDAO.update(user.copy(password = Some(HashedPassword.fromPlain("barfoo")))))
        await(userDAO.authenticateByEmail(mocks.privilegedUser.email, "barfoo")) must beSome
      }
    }
  }


  "openid assoc" should {
    "find accounts by openid_url and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assocOpt = await(userDAO.openId.findByUrl(mocks.yahooOpenId.url))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mocks.moderator.email)

          val assoc2Opt = await(userDAO.openId.addAssociation(user.id, "another-test-url"))
          assoc2Opt must beSome.which { assoc2 =>
            assoc2.user must beSome.which { u =>
              u must equalTo(user)
            }
          }
        }
      }
    }
  }

  "oauth2 assoc" should {
    "find accounts by oauth2 provider info and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assocOpt = await(userDAO.oAuth2.findByProviderInfo(mocks.googleOAuthAssoc.providerId, mocks.googleOAuthAssoc.provider))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mocks.privilegedUser.email)

          val assoc2Opt = await(userDAO.oAuth2.addAssociation(user.id, "4321", "facebook"))
          assoc2Opt must beSome.which { assoc2 =>
            assoc2.user must beSome.which { u =>
              u must equalTo(user)
            }
          }
        }
      }
    }
  }
}
