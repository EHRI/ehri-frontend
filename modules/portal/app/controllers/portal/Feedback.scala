package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Result, RequestHeader, Controller}
import controllers.base.{AuthController, ControllerHelpers}
import scala.concurrent.Future.{successful => immediate}
import backend.{Backend, FeedbackDAO}
import models.AccountDAO
import com.google.inject._
import utils.SessionPrefs
import com.typesafe.plugin.MailerAPI
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Feedback @Inject()(implicit globalConfig: global.GlobalConfig, feedbackDAO: FeedbackDAO,
                              backend: Backend, userDAO: AccountDAO, mailer: MailerAPI)
  extends PortalController with Secured {

  implicit def prefs = new SessionPrefs

  import utils.forms._
  import play.api.data.Form
  import play.api.data.Forms._

  case class CheckFeedbackData(
    timestamp: String,
    blankCheck: String
  ) extends HoneyPotForm with TimeCheckForm

  private def checkFbForm(implicit app: play.api.Application) = Form(
    mapping(
      TIMESTAMP -> nonEmptyText,
      BLANK_CHECK -> text.verifying(s => s.isEmpty)
    )(CheckFeedbackData.apply)(CheckFeedbackData.unapply)
      verifying blankFieldIsBlank verifying formSubmissionTime
  )

  def feedback = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.p.feedback(models.Feedback.form))
  }

  private def getCopyMail(feedbackType: Option[models.Feedback.Type.Value])(implicit app: play.api.Application): Option[String] = {
    val defaultOpt = app.configuration.getString("ehri.portal.feedback.copyTo")
    (for {
      ft <- feedbackType
      ct <- app.configuration.getString(s"ehri.portal.feedback.$ft.copyTo")
    } yield ct) orElse defaultOpt
  }

  private def sendMessageEmail(feedback: models.Feedback)(implicit app: play.api.Application, request: RequestHeader): Unit = {
    for {
      accTo <- getCopyMail(feedback.`type`)
    } yield {
      val text = feedback.text.getOrElse("No message provided")
      mailer
        .setSubject("EHRI Portal Feedback")
        .setRecipient(accTo)
        .setReplyTo(feedback.email.getOrElse("noreply@ehri-project.eu"))
        .setFrom("EHRI User <noreply@ehri-project.eu>")
        .send(text, text)
    }
  }

  def feedbackPost = userProfileAction.async { implicit userOpt => implicit request =>
    val boundForm: Form[models.Feedback] = models.Feedback.form.bindFromRequest()
    import play.api.Play.current

    def response(f: Form[models.Feedback]): Result =
      if (isAjax) BadRequest(f.errorsAsJson) else BadRequest(views.html.p.feedback(f))

    // check the anti-bot measures and immediately return the original
    // form. No feedback needed since they're (hopefully) a bot.
    if (checkFbForm.bindFromRequest.hasErrors) immediate(response(boundForm))
    else boundForm.fold(
      errorForm => immediate(response(errorForm)),
      feedback => {
        val moreFeedback = userOpt.map { user =>
          feedback.copy(name = feedback.name.orElse(user.account.map(_.id)))
            .copy(email = feedback.email.orElse(user.account.map(_.email)))
        }.getOrElse(feedback)
          .copy(context = Some(models.FeedbackContext.fromRequest),
            mode = Some(play.api.Play.current.mode))
        feedbackDAO.create(moreFeedback).map { id =>
          sendMessageEmail(moreFeedback)
          if (isAjax) Ok(id)
          else Redirect(controllers.portal.routes.Portal.index())
            .flashing("success" -> "portal.feedback.thanks.message")
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
