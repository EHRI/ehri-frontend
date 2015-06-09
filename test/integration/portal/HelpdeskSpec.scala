package integration.portal

import helpers.IntegrationTestRunner
import play.api.test.FakeRequest

class HelpdeskSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  "Helpdesk views" should {
    "not allow empty queries" in new ITestApp {
      val origCount = helpdeskBuffer.size
      val data = Map(
        "query" -> Seq(""),
        "email" -> Seq("test@example.com"),
        "copyMe" -> Seq("false")
      )

      val post = FakeRequest(controllers.portal.routes.Helpdesk.helpdeskPost())
        .withUser(privilegedUser).withCsrf.callWith(data)
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

      val post = FakeRequest(controllers.portal.routes.Helpdesk.helpdeskPost())
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(post) must equalTo(OK)
      mailBuffer.size must equalTo(mailsBefore + 1)
      mailBuffer.last.bodyText.getOrElse("") must contain(testMailContent)
    }

    "give the right results" in new ITestApp {
      val origCount = helpdeskBuffer.size
      val data1 = Map(
        "query" -> Seq("Stuff in the netherlands"),
        "email" -> Seq("test@example.com"),
        "copyMe" -> Seq("false")
      )

      val try1 = FakeRequest(controllers.portal.routes.Helpdesk.helpdeskPost())
        .withUser(privilegedUser).withCsrf.callWith(data1)
      status(try1) must equalTo(OK)
      helpdeskBuffer.size must equalTo(origCount + 1)
      contentAsString(try1) must contain(
        controllers.portal.routes.Repositories.browse("r1").url)

      // if the query doesn't contain 'netherlands' we should get
      // 'r2' back as the recommended institution...
      val try2 = FakeRequest(controllers.portal.routes.Helpdesk.helpdeskPost())
        .withUser(privilegedUser).withCsrf
        .callWith(data1.updated("query", Seq("Stuff in the UK")))
      status(try2) must equalTo(OK)
      helpdeskBuffer.size must equalTo(origCount + 2)
      contentAsString(try2) must not contain
        controllers.portal.routes.Repositories.browse("r1").url
      contentAsString(try2) must contain(
        controllers.portal.routes.Repositories.browse("r2").url)
    }
  }
}
