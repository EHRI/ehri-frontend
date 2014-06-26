package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{RequestHeader, Controller}
import controllers.base.{AuthController, ControllerHelpers}
import scala.concurrent.Future.{successful => immediate}
import backend.{Backend, FeedbackDAO}
import models.AccountDAO
import play.api.Play.current
import com.google.inject._
import utils.SessionPrefs

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Feedback @Inject()(implicit globalConfig: global.GlobalConfig, feedbackDAO: FeedbackDAO, backend: Backend, userDAO: AccountDAO) extends Controller with ControllerHelpers with AuthController {

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

  implicit def prefs = new SessionPrefs

  def feedback = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.p.feedback(models.Feedback.form))
  }

  private def sendMessageEmail(feedback: models.Feedback)(implicit request: RequestHeader): Unit = {
    for {
      accTo <- current.configuration.getString("ehri.portal.feedback.copyTo")
    } yield {
      import com.typesafe.plugin._
      val text = feedback.text.getOrElse("No message provided")
      use[MailerPlugin].email
        .setSubject("EHRI Portal Feedback")
        .setRecipient(accTo)
        .setReplyTo(feedback.email.getOrElse("noreply@ehri-project.eu"))
        .setFrom("EHRI User <noreply@ehri-project.eu>")
        .send(text, text)
    }
  }

  def feedbackPost = userProfileAction.async { implicit userOpt => implicit request =>
    models.Feedback.form.bindFromRequest.fold(
      errorForm => {
        if (isAjax) immediate(BadRequest(errorForm.errorsAsJson))
        else immediate(BadRequest(views.html.p.feedback(errorForm)))
      },
      feedback => {
        val moreFeedback = userOpt.flatMap(_.account).map { account =>
          feedback
            .copy(name = feedback.name.orElse(Some(account.id)))
            .copy(email = feedback.email.orElse(Some(account.email)))
        }.getOrElse(feedback)
          .copy(
            context = Some(models.FeedbackContext.fromRequest),
            mode = Some(play.api.Play.current.mode))
        feedbackDAO.create(moreFeedback).map { id =>
          sendMessageEmail(moreFeedback)
          if (isAjax) Ok(id)
          else Redirect(controllers.portal.routes.Portal.index())
            .flashing("success" -> "Thanks for your feedback!")
        }
      }
    )
  }

  def list = adminAction.async { implicit userOpt => implicit request =>
    feedbackDAO.list("order" -> "-createdAt").map { flist =>
      Ok(views.html.p.feedbackList(flist.filter(_.text.isDefined)))
    }
  }
}
