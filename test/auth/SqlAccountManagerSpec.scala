package auth

import java.util.UUID

import anorm.SqlParser._
import anorm._
import auth.sql.SqlAccountManager
import helpers.WithSqlFixtures
import models.Account
import org.h2.jdbc.JdbcSQLException
import org.joda.time.DateTime
import play.api.db.DB
import play.api.test.{FakeApplication, PlaySpecification}
import utils.PageParams


class SqlAccountManagerSpec extends PlaySpecification {

  implicit val dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  def accounts: AccountManager = SqlAccountManager()(play.api.Play.current)

  "account manager" should {
    "load fixtures with the right number of accounts" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        SQL"select count(*) from users".as(scalar[Long].single) must equalTo(5L)
      }
    }

    "enforce email uniqueness" in new WithSqlFixtures(new FakeApplication) {
      DB.withConnection { implicit connection =>
        SQL"insert into users (id,email,verified,staff) values ('blah', ${mocks.privilegedUser.email}},1,1)"
          .executeInsert() must throwA[JdbcSQLException]
      }
    }

    "create accounts with correct properties" in new WithSqlFixtures(new FakeApplication) {
      val now = DateTime.now
      val testAcc = Account(
        id = "test",
        email = "blah@example.com",
        verified = false,
        active = true,
        staff = false,
        allowMessaging = true,
        password = Some(HashedPassword.fromPlain("p4ssword"))
      )
      val acc = await(accounts.create(testAcc))
      acc.id must equalTo(testAcc.id)
      acc.email must equalTo(testAcc.email)
      acc.verified must equalTo(testAcc.verified)
      acc.active must equalTo(testAcc.active)
      acc.staff must equalTo(testAcc.staff)
      acc.allowMessaging must equalTo(testAcc.allowMessaging)
      acc.created must beSome.which(_ must beGreaterThan(now))
      acc.lastLogin must beNone
      acc.password must beSome.which(_.check("p4ssword") must beTrue)
    }

    "find multiple accounts by id" in new WithSqlFixtures(new FakeApplication) {
      val accountsById: Seq[Account] = await(accounts.findAllById(
        Seq(mocks.privilegedUser.id, mocks.unprivilegedUser.id)))
      accountsById.find(_.id == mocks.privilegedUser.id) must beSome
      accountsById.find(_.id == mocks.unprivilegedUser.id) must beSome
    }

    "find accounts by token and expire tokens" in new WithSqlFixtures(new FakeApplication) {
      val uuid = UUID.randomUUID()
      await(accounts.createToken(mocks.privilegedUser.id, uuid, isSignUp = true))
      await(accounts.findByToken(uuid.toString, isSignUp = true)) must beSome.which { acc =>
        acc.id must equalTo(mocks.privilegedUser.id)
      }
      await(accounts.expireTokens(mocks.privilegedUser.id))
      await(accounts.findByToken(uuid.toString, isSignUp = true)) must beNone
    }

    "verify accounts via token" in new WithSqlFixtures(new FakeApplication) {
      val uuid = UUID.randomUUID()
      await(accounts.createToken(mocks.unverifiedUser.id, uuid, isSignUp = false))
      mocks.unverifiedUser.verified must beFalse
      await(accounts.verify(mocks.unverifiedUser, uuid.toString)) must beSome
      await(accounts.findById(mocks.unprivilegedUser.id)) must beSome.which { check =>
        check.verified must beTrue
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
