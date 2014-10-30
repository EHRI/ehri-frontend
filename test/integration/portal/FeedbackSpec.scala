package integration.portal

import helpers.IntegrationTestRunner

class FeedbackSpec extends IntegrationTestRunner {

  "Feedback views" should {
    "allow anon feedback" in new ITestApp {
      val fbCount = feedbackBuffer.size
      val fb = Map("text" -> Seq("it doesn't work"))
      val post = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
          controllers.portal.routes.Feedback.feedbackPost().url), fb).get
      status(post) must equalTo(SEE_OTHER)
      val newCount = feedbackBuffer.size
      newCount must equalTo(fbCount + 1)
      feedbackBuffer.get(newCount) must beSome.which { f =>
        f.text must equalTo(Some("it doesn't work"))
      }
    }
  }
}
