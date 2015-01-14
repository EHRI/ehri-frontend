package integration.portal

import helpers.IntegrationTestRunner

class AccountsSpec extends IntegrationTestRunner {
  import mocks.privilegedUser

  private val accountsSpec = controllers.portal.account.routes.Accounts

  "Account views" should {
    "redirect to index page on log out" in new ITestApp {
      val logout = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        accountsSpec.logout().url)).get
      status(logout) must equalTo(SEE_OTHER)
      flash(logout).get("success") must beSome.which { fl =>
        // NB: No i18n here...
        fl must contain("logout.confirmation")
      }
    }

    "allow user to login with password" in new ITestApp {
    }
  }
}
