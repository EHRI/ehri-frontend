package services.accounts

import anorm.SqlParser._
import anorm._
import auth.HashedPassword
import helpers.IntegrationTestRunner
import models.Account
import play.api.Application
import utils.PageParams

import java.sql.{SQLException, SQLIntegrityConstraintViolationException}
import java.time.ZonedDateTime
import java.util.UUID


class SqlAccountManagerSpec extends IntegrationTestRunner {

  private implicit val dateTimeOrdering: Ordering[ZonedDateTime] = Ordering.fromLessThan(_ isBefore _)

  private def accounts(implicit app: Application): AccountManager = app.injector.instanceOf[SqlAccountManager]

  "account manager" should {
    "load fixtures with the right number of accounts" in new DBTestApp("user-fixtures.sql") {
      db.withConnection { implicit connection =>
        SQL"select count(*) from users".as(scalar[Long].single) must equalTo(5L)
      }
    }

    "enforce email uniqueness" in new DBTestApp("user-fixtures.sql") {
      db.withConnection { implicit connection =>
        SQL"""insert into users (id,email,verified,staff)
              values ('blah',${mockdata.privilegedUser.email}, true, true)"""
          .executeInsert() must throwA[SQLIntegrityConstraintViolationException]
            .or(throwA[SQLException]) // H2 throws these, annoyingly
      }
    }

    "create accounts with correct properties" in new DBTestApp("user-fixtures.sql") {
      // "now" with fudge factor for crap DBs that don't support
      // fractional timestamps...
      val now = ZonedDateTime.now().minusSeconds(1)
      val testAcc = Account(
        id = "test",
        email = "blah@example.com",
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

    "find multiple accounts by id" in new DBTestApp("user-fixtures.sql") {
      val accountsById: Seq[Account] = await(accounts.findAllById(
        Seq(mockdata.privilegedUser.id, mockdata.unprivilegedUser.id)))
      accountsById.find(_.id == mockdata.privilegedUser.id) must beSome
      accountsById.find(_.id == mockdata.unprivilegedUser.id) must beSome
    }

    "handle empty id lists in multiple account queries" in new DBTestApp("user-fixtures.sql") {
      val accountsById: Seq[Account] = await(accounts.findAllById(Seq.empty))
      accountsById must beEmpty
    }

    "find accounts by token and expire tokens" in new DBTestApp("user-fixtures.sql") {
      val uuid = UUID.randomUUID()
      await(accounts.createToken(mockdata.privilegedUser.id, uuid, isSignUp = true))
      await(accounts.findByToken(uuid.toString, isSignUp = true)) must beSome.which { acc =>
        acc.id must equalTo(mockdata.privilegedUser.id)
      }
      await(accounts.expireTokens(mockdata.privilegedUser.id))
      await(accounts.findByToken(uuid.toString, isSignUp = true)) must beNone
    }

    "set accounts logged in" in new DBTestApp("user-fixtures.sql") {
      val user = await(accounts.get(mockdata.privilegedUser.id))
      user.lastLogin must beNone
      await(accounts.setLoggedIn(user))
      val user2 = await(accounts.get(user.id))
      user2.lastLogin must beSome
    }

    "verify accounts via token" in new DBTestApp("user-fixtures.sql") {
      val uuid = UUID.randomUUID()
      await(accounts.createToken(mockdata.unverifiedUser.id, uuid, isSignUp = false))
      mockdata.unverifiedUser.verified must beFalse
      await(accounts.verify(mockdata.unverifiedUser, uuid.toString)) must beSome
      await(accounts.findById(mockdata.unprivilegedUser.id)) must beSome.which { check =>
        check.verified must beTrue
      }
    }

    "find all accounts with pagination" in new DBTestApp("user-fixtures.sql") {
      await(accounts.findAll()).size must equalTo(5)
      await(accounts.findAll(PageParams(limit = 2))).size must equalTo(2)
    }

    "find all accounts with filters" in new DBTestApp("user-fixtures.sql") {
      await(accounts.findAll(filters = AccountFilters(staff = None))).size must equalTo(5)
      await(accounts.findAll(filters = AccountFilters(staff = Some(true)))).size must equalTo(3)
      await(accounts.findAll(filters = AccountFilters(staff = Some(false)))).size must equalTo(2)
    }

    "find accounts by id" in new DBTestApp("user-fixtures.sql") {
      db.withConnection { implicit connection =>
        await(accounts.findById(mockdata.privilegedUser.id)) must beSome
      }
    }

    "find accounts by email, case insensitively" in new DBTestApp("user-fixtures.sql") {
      db.withConnection { implicit connection =>
        await(accounts.findByEmail(mockdata.privilegedUser.email.toUpperCase())) must beSome
      }
    }

    "allow deactivating accounts" in new DBTestApp("user-fixtures.sql") {
      db.withConnection { implicit connection =>
        await(accounts.findById(mockdata.privilegedUser.id)) must beSome.which { user =>
          user.active must beTrue
          await(accounts.update(user.copy(active = false)))
          await(accounts.findById(mockdata.privilegedUser.id)) must beSome.which { inactive =>
            inactive.active must beFalse
          }
        }
      }
    }

    "allow setting and updating user's passwords" in new DBTestApp("user-fixtures.sql") {
      val userOpt: Option[Account] = await(accounts.findByEmail(mockdata.privilegedUser.email))
      userOpt must beSome.which { user =>
        await(accounts.update(user.copy(password = Some(HashedPassword.fromPlain("foobar")))))
        await(accounts.authenticateByEmail(mockdata.privilegedUser.email, "foobar")) must beSome

        await(accounts.update(user.copy(password = Some(HashedPassword.fromPlain("barfoo")))))
        await(accounts.authenticateByEmail(mockdata.privilegedUser.email, "barfoo")) must beSome
      }
    }
  }


  "openid assoc" should {
    "find accounts by openid_url and allow adding another" in new DBTestApp("user-fixtures.sql") {
      val assocOpt = await(accounts.openId.findByUrl(mockdata.yahooOpenId.url))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mockdata.moderator.email)

          val assoc2Opt = await(accounts.openId.addAssociation(user.id, "another-test-url"))
          assoc2Opt must beSome.which { assoc2 =>
            assoc2.user must beSome.which { u =>
              u must equalTo(user)
            }
          }
        }
      }
    }

    "find all associations" in new DBTestApp("user-fixtures.sql") {
      await(accounts.openId.findAll).size must equalTo(1)
    }
  }

  "oauth2 assoc" should {
    "find accounts by oauth2 provider info and allow adding another" in new DBTestApp("user-fixtures.sql") {
      val assocOpt = await(accounts.oAuth2.findByProviderInfo(mockdata.googleOAuthAssoc.providerId, mockdata.googleOAuthAssoc.provider))
      assocOpt must beSome.which { assoc =>
        assoc.user must beSome.which { user =>
          user.email must beEqualTo(mockdata.privilegedUser.email)

          val assoc2Opt = await(accounts.oAuth2.addAssociation(user.id, "4321", "facebook"))
          assoc2Opt must beSome.which { assoc2 =>
            assoc2.user must beSome.which { u =>
              u must equalTo(user)
            }
          }
        }
      }
    }

    "find all associations" in new DBTestApp("user-fixtures.sql") {
      await(accounts.oAuth2.findAll).size must equalTo(2)
    }
  }
}
