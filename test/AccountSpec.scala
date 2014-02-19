package test

import play.api.db.DB
import anorm._
import anorm.SqlParser._
import helpers.WithFixures
import models.{Account, AccountDAO}
import models.sql.{OAuth2Association, OpenIDAssociation}
import play.api.test.PlaySpecification


/**
 * Spec for testing individual data access components work as expected.
 */
class AccountSpec extends PlaySpecification {

  "account db" should {
    "load fixtures with the right number of accounts" in new WithFixures {
      DB.withConnection { implicit connection =>
        SQL("select count(*) from users").as(scalar[Long].single) must equalTo(4L)
      }
    }

    "find accounts by id and email" in new WithFixures {
      DB.withConnection { implicit connection =>
        val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get
        userDAO.findByProfileId(mocks.privilegedUser.id) must beSome
        userDAO.findByEmail(mocks.privilegedUser.email) must beSome
      }
    }

    "allow setting and updating user's passwords" in new WithFixures {
      val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get
      val userOpt: Option[Account] = userDAO.findByEmail(mocks.privilegedUser.email)
      userOpt must beSome.which { user =>
        val hashedPw = Account.hashPassword("foobar")
        user.setPassword(hashedPw)
        userDAO.authenticate(mocks.privilegedUser.email, "foobar") must beSome

        val hashedPw2 = Account.hashPassword("barfoo")
        user.setPassword(hashedPw2)
        userDAO.authenticate(mocks.privilegedUser.email, "barfoo") must beSome
      }
    }
  }


  "openid assoc" should {
    "find accounts by openid_url and allow adding another" in new WithFixures {
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
    "find accounts by oauth2 provider info and allow adding another" in new WithFixures {
      val assoc = OAuth2Association.findByProviderInfo("1234", "google")
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
