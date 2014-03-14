package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import controllers.base.{AuthController, ControllerHelpers}
import scala.concurrent.Future.{successful => immediate}
import backend.{Backend, FeedbackDAO}
import models.AccountDAO
import play.api.Play.current
import com.google.inject._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Feedback @Inject()(implicit globalConfig: global.GlobalConfig, feedbackDAO: FeedbackDAO, backend: Backend, userDAO: AccountDAO) extends Controller with ControllerHelpers with AuthController {

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

  def feedback = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.p.feedback(models.Feedback.form))
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
          if (isAjax) Ok(id)
          else Redirect(controllers.portal.routes.Portal.index())
            .flashing("success" -> "Thanks for your feedback!")
        }
      }
    )
  }
}
