package integration.portal

import helpers.IntegrationTestRunner

class HelpdeskSpec extends IntegrationTestRunner {

  "Helpdesk views" should {
    "not allow empty queries" in new ITestApp {
      val origCount = helpdeskBuffer.size
      val data = Map(
        "query" -> Seq(""),
        "email" -> Seq("test@example.com"),
        "copyMe" -> Seq("false")
      )

      val post = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
        controllers.portal.routes.Helpdesk.helpdeskPost().url), data).get
      status(post) must equalTo(BAD_REQUEST)
      helpdeskBuffer.size must equalTo(origCount)
    }

    "copy messages to the end-user" in new ITestApp {
      val testMailContent = "Blah"
      val mailsBefore = mailBuffer.size
      val data = Map(
        "query" -> Seq(testMailContent),
        "email" -> Seq("test@example.com"),
        "copyMe" -> Seq("true")
      )

      val post = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
        controllers.portal.routes.Helpdesk.helpdeskPost().url), data).get
      status(post) must equalTo(OK)
      mailBuffer.size must equalTo(mailsBefore + 1)
      mailBuffer.last.text must contain(testMailContent)
    }

    "give the right results" in new ITestApp {
      val origCount = helpdeskBuffer.size
      val data1 = Map(
        "query" -> Seq("Stuff in the netherlands"),
        "email" -> Seq("test@example.com"),
        "copyMe" -> Seq("false")
      )

      val try1 = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
        controllers.portal.routes.Helpdesk.helpdeskPost().url), data1).get
      status(try1) must equalTo(OK)
      helpdeskBuffer.size must equalTo(origCount + 1)
      contentAsString(try1) must contain(
        controllers.portal.routes.Repositories.browse("r1").url)

      // if the query doesn't contain 'netherlands' we should get
      // 'r2' back as the recommended institution...
      val try2 = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
        controllers.portal.routes.Helpdesk.helpdeskPost().url),
        data1.updated("query", Seq("Stuff in the UK"))).get
      status(try2) must equalTo(OK)
      helpdeskBuffer.size must equalTo(origCount + 2)
      contentAsString(try2) must not contain
        controllers.portal.routes.Repositories.browse("r1").url
      contentAsString(try2) must contain(
        controllers.portal.routes.Repositories.browse("r2").url)
    }
  }
}
