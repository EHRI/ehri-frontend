package services.feedback

import helpers.IntegrationTestRunner
import models.Feedback
import play.api.Application
import utils.{Page, PageParams}


class SqlFeedbackServiceSpec extends IntegrationTestRunner {

  private def feedbackService(implicit app: Application) = app.injector.instanceOf[SqlFeedbackService]

  "Feedback service" should {
    "locate items correctly" in new DBTestApp("feedback-fixtures.sql") {
      val q = await(feedbackService.get("nVlf4EpZjN"))
      q.text must beSome("Testing...")
    }

    "list items correctly" in new DBTestApp("feedback-fixtures.sql") {
      val feedback: Page[Feedback] = await(feedbackService.list(PageParams.empty))
      feedback.total must_== 2
      feedback.items.map(_.objectId).sorted must_== Seq(Some("SmRoRZ7U4j"), Some("nVlf4EpZjN"))
    }

    "delete items correctly" in new DBTestApp("feedback-fixtures.sql") {
      await(feedbackService.delete("nVlf4EpZjN")) must_== true
      await(feedbackService.list(PageParams.empty)).total must_== 1
    }

    "create items correctly" in new DBTestApp("feedback-fixtures.sql") {
      val f = Feedback(text = Some("Problem..."), mode = Some(play.api.Mode.Prod))
      val id = await(feedbackService.create(f))
      val f2 = await(feedbackService.get(id))
      f2.mode must beSome(play.api.Mode.Prod)
      // If we delete it successfully assume all good...
      await(feedbackService.delete(id)) must_== true
    }
  }
}
