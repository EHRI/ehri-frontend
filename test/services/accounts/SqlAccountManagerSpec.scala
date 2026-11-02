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
      await(accounts.createToken(mockdata.unverifiedUser.id, uuid, isSignUp = true))
      mockdata.unverifiedUser.verified must beFalse
      await(accounts.verify(uuid.toString)) must beSome.which(_.verified must beTrue)
      // Tokens can only be used once
      await(accounts.verify(uuid.toString)) must beNone
    }

    "not verify accounts with a password reset token" in new DBTestApp("user-fixtures.sql") {
      val uuid = UUID.randomUUID()
      await(accounts.createToken(mockdata.unverifiedUser.id, uuid, isSignUp = false))
      await(accounts.verify(uuid.toString)) must beNone
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

    "reset the password of a verified account, keeping its OAuth2 logins" in new DBTestApp("user-fixtures.sql") {
      val id = mockdata.privilegedUser.id
      val token = UUID.randomUUID()
      await(accounts.createToken(id, token, isSignUp = false))
      await(accounts.resetPassword(token.toString, HashedPassword.fromPlain("n3wpassw0rd"))) must beSome.which { account =>
        account.password.exists(_.check("n3wpassw0rd")) must beTrue
      }
      await(accounts.oAuth2.findForAccount(id)) must not(beEmpty)
    }

    "claim an unverified account when resetting its password" in new DBTestApp("user-fixtures.sql") {
      val id = mockdata.unverifiedUser.id
      val token = UUID.randomUUID()
      await(accounts.createToken(id, token, isSignUp = false))
      await(accounts.oAuth2.addAssociation(id, "4321", "microsoft"))
      await(accounts.resetPassword(token.toString, HashedPassword.fromPlain("n3wpassw0rd"))) must beSome.which { account =>
        account.verified must beTrue
        account.password.exists(_.check("n3wpassw0rd")) must beTrue
      }
      await(accounts.oAuth2.findForAccount(id)) must beEmpty
    }

    "only allow a password reset token to be used once" in new DBTestApp("user-fixtures.sql") {
      val token = UUID.randomUUID()
      await(accounts.createToken(mockdata.privilegedUser.id, token, isSignUp = false))
      await(accounts.resetPassword(token.toString, HashedPassword.fromPlain("n3wpassw0rd"))) must beSome
      await(accounts.resetPassword(token.toString, HashedPassword.fromPlain("an0th3rpass"))) must beNone
    }

    "not reset a password with a sign up token" in new DBTestApp("user-fixtures.sql") {
      val token = UUID.randomUUID()
      await(accounts.createToken(mockdata.privilegedUser.id, token, isSignUp = true))
      await(accounts.resetPassword(token.toString, HashedPassword.fromPlain("n3wpassw0rd"))) must beNone
    }

    "set individual account fields without changing others" in new DBTestApp("user-fixtures.sql") {
      val id = mockdata.unverifiedUser.id
      val withPassword = await(accounts.setPassword(id, HashedPassword.fromPlain("n3wpassw0rd")))
      withPassword.password.exists(_.check("n3wpassw0rd")) must beTrue
      withPassword.verified must beFalse
      val withMessaging = await(accounts.setAllowMessaging(id, allowMessaging = false))
      withMessaging.allowMessaging must beFalse
      withMessaging.password.exists(_.check("n3wpassw0rd")) must beTrue
      val withFlags = await(accounts.setAdminFlags(id, active = true, staff = true, verified = true))
      withFlags.verified must beTrue
      withFlags.staff must beTrue
      withFlags.password.exists(_.check("n3wpassw0rd")) must beTrue
    }

    "change an account's email, unverifying it and expiring its tokens" in new DBTestApp("user-fixtures.sql") {
      val id = mockdata.privilegedUser.id
      val token = UUID.randomUUID()
      await(accounts.createToken(id, token, isSignUp = true))
      val account = await(accounts.changeEmail(id, "new@example.com"))
      account.email must equalTo("new@example.com")
      account.verified must beFalse
      await(accounts.findByToken(token.toString, isSignUp = true)) must beNone
    }

    "claim an account, removing its other logins" in new DBTestApp("user-fixtures.sql") {
      val id = mockdata.privilegedUser.id
      await(accounts.createToken(id, UUID.randomUUID(), isSignUp = true))
      val account = await(accounts.claimWithOAuth2(id, "4321", "microsoft"))
      account.verified must beTrue
      account.password must beNone
      await(accounts.oAuth2.findForAccount(id)).map(_.provider) must equalTo(Seq("microsoft"))
      await(accounts.oAuth2.findAll).size must equalTo(2)
    }
  }
}
