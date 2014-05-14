package integration.portal

import helpers.Neo4jRunnerSpec

class FeedbackSpec extends Neo4jRunnerSpec(classOf[FeedbackSpec]) {

  "Feedback views" should {
    "allow anon feedback" in new FakeApp {
      val fbCount = mockFeedback.buffer.size
      val fb = Map("text" -> Seq("it doesn't work"))
      val post = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
          controllers.portal.routes.Feedback.feedbackPost().url), fb).get
      status(post) must equalTo(SEE_OTHER)
      val newCount = mockFeedback.buffer.size
      newCount must equalTo(fbCount + 1)
      mockFeedback.buffer.get(newCount) must beSome.which { f =>
        f.text must equalTo(Some("it doesn't work"))
      }
    }
  }
}
