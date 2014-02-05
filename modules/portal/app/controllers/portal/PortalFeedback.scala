package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import controllers.base.{AuthController, ControllerHelpers}
import models.{FeedbackContext, Feedback}
import scala.concurrent.Future.{successful => immediate}
import backend.FeedbackDAO

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalFeedback {
  self: Controller with ControllerHelpers with AuthController =>

  val feedbackDAO: FeedbackDAO

  def feedback = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.p.feedback(Feedback.form))
  }

  def feedbackPost = userProfileAction.async { implicit userOpt => implicit request =>
    Feedback.form.bindFromRequest.fold(
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
            context = Some(FeedbackContext.fromRequest),
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
