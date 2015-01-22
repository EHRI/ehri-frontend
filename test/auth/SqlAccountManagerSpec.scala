package auth

import java.util.UUID

import anorm.SqlParser._
import anorm._
import auth.sql.SqlAccountManager
import helpers.WithSqlFixtures
import models.Account
import org.h2.jdbc.JdbcSQLException
import play.api.db.DB
import play.api.test.{FakeApplication, PlaySpecification}
import utils.PageParams


class SqlAccountManagerSpec extends PlaySpecification {

  def accounts: AccountManager = SqlAccountManager()(play.api.Play.current)

  "account manager" should {
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
      val accountsById: Seq[Account] = await(accounts.findAllById(
        Seq(mocks.privilegedUser.id, mocks.unprivilegedUser.id)))
      accountsById.find(_.id == mocks.privilegedUser.id) must beSome
      accountsById.find(_.id == mocks.unprivilegedUser.id) must beSome
    }

    "find accounts by token" in new WithSqlFixtures(new FakeApplication) {
      val uuid = UUID.randomUUID()
      await(accounts.createToken(mocks.privilegedUser.id, uuid, isSignUp = true))
      await(accounts.findByToken(uuid.toString, isSignUp = true)) must beSome.which { acc =>
        acc.id must equalTo(mocks.privilegedUser.id)
      }
    }

    "find all accounts with pagination" in new WithSqlFixtures(new FakeApplication) {
      await(accounts.findAll()).size must equalTo(5)
      await(accounts.findAll(PageParams(limit = 2))).size must equalTo(2)
    }

    "find accounts by id and email" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        await(accounts.findById(mocks.privilegedUser.id)) must beSome
        await(accounts.findByEmail(mocks.privilegedUser.email)) must beSome
      }
    }

    "allow deactivating accounts" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        await(accounts.findById(mocks.privilegedUser.id)) must beSome.which { user =>
          user.active must beTrue
          await(accounts.update(user.copy(active = false)))
          await(accounts.findById(mocks.privilegedUser.id)) must beSome.which { inactive =>
            inactive.active must beFalse
          }
        }
      }
    }

    "allow setting and updating user's passwords" in new WithSqlFixtures(new FakeApplication) {
      val userOpt: Option[Account] = await(accounts.findByEmail(mocks.privilegedUser.email))
      userOpt must beSome.which { user =>
        await(accounts.update(user.copy(password = Some(HashedPassword.fromPlain("foobar")))))
        await(accounts.authenticateByEmail(mocks.privilegedUser.email, "foobar")) must beSome

        await(accounts.update(user.copy(password = Some(HashedPassword.fromPlain("barfoo")))))
        await(accounts.authenticateByEmail(mocks.privilegedUser.email, "barfoo")) must beSome
      }
    }
  }


  "openid assoc" should {
    "find accounts by openid_url and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assocOpt = await(accounts.openId.findByUrl(mocks.yahooOpenId.url))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mocks.moderator.email)

          val assoc2Opt = await(accounts.openId.addAssociation(user.id, "another-test-url"))
          assoc2Opt must beSome.which { assoc2 =>
            assoc2.user must beSome.which { u =>
              u must equalTo(user)
            }
          }
        }
      }
    }

    "find all associations" in new WithSqlFixtures(new FakeApplication) {
      await(accounts.openId.findAll).size must equalTo(1)
    }
  }

  "oauth2 assoc" should {
    "find accounts by oauth2 provider info and allow adding another" in new WithSqlFixtures(new FakeApplication) {
      val assocOpt = await(accounts.oAuth2.findByProviderInfo(mocks.googleOAuthAssoc.providerId, mocks.googleOAuthAssoc.provider))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mocks.privilegedUser.email)

          val assoc2Opt = await(accounts.oAuth2.addAssociation(user.id, "4321", "facebook"))
          assoc2Opt must beSome.which { assoc2 =>
            assoc2.user must beSome.which { u =>
              u must equalTo(user)
            }
          }
        }
      }
    }

    "find all associations" in new WithSqlFixtures(new FakeApplication) {
      await(accounts.oAuth2.findAll).size must equalTo(2)
    }
  }
}
