package integration.portal

import helpers.IntegrationTestRunner
import play.api.test.FakeRequest
import utils.forms._
import scala.Some

class FeedbackSpec extends IntegrationTestRunner {

  override def getConfig = Map(
    "ehri.signup.timeCheckSeconds" -> -1
  )

  "Feedback views" should {

    import utils.forms.HoneyPotForm._
    import utils.forms.TimeCheckForm._
    import models.Feedback._

    val fb = Map(
      TEXT -> Seq("it doesn't work"),
      TIMESTAMP -> Seq(org.joda.time.DateTime.now.toString),
      BLANK_CHECK -> Seq("")
    )

    "allow anon feedback" in new ITestApp {
      val fbCount = feedbackBuffer.size
      val post = route(fakeRequest(POST,
          controllers.portal.routes.Feedback.feedbackPost().url), fb).get
      status(post) must equalTo(SEE_OTHER)
      val newCount = feedbackBuffer.size
      newCount must equalTo(fbCount + 1)
      feedbackBuffer.get(newCount) must beSome.which { f =>
        f.text must equalTo(Some("it doesn't work"))
        f.email must beNone
      }
    }

    "allow logged-in feedback" in new ITestApp {
      val fbCount = feedbackBuffer.size
      val post = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
        controllers.portal.routes.Feedback.feedbackPost().url), fb).get
      status(post) must equalTo(SEE_OTHER)
      val newCount = feedbackBuffer.size
      newCount must equalTo(fbCount + 1)
      feedbackBuffer.get(newCount) must beSome.which { f =>
        f.text must equalTo(Some("it doesn't work"))
        f.email must equalTo(Some(mocks.privilegedUser.email))
      }
    }

    val testDataMail = "data@broken-site.com"
    val testSiteMail = "site@broken-site.com"

    "copy data feedback to the right address" in new ITestApp(specificConfig = Map(
      "ehri.portal.feedback.data.copyTo" -> testDataMail
    )) {
      val dataFb = fb.updated(TYPE, Seq(models.Feedback.Type.Data.toString))
      val post = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
          controllers.portal.routes.Feedback.feedbackPost().url), dataFb).get
      status(post) must equalTo(SEE_OTHER)
      mailBuffer.lastOption must beSome.which { m =>
        m.to.headOption must equalTo(Some(testDataMail))
      }
    }

    "copy site feedback to the right address" in new ITestApp(specificConfig = Map(
      "ehri.portal.feedback.site.copyTo" -> testSiteMail
    )) {
      val dataFb = fb.updated(TYPE, Seq(models.Feedback.Type.Site.toString))
      val post = route(fakeLoggedInHtmlRequest(mocks.privilegedUser, POST,
        controllers.portal.routes.Feedback.feedbackPost().url), dataFb).get
      status(post) must equalTo(SEE_OTHER)
      mailBuffer.lastOption must beSome.which { m =>
        m.to.headOption must equalTo(Some(testSiteMail))
      }
    }
  }
}
