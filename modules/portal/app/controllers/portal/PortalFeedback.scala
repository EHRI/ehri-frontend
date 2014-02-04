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

  def feedback = optionalUserAction.async { implicit accountOpt => implicit request =>
    ???
  }

  def feedbackPost = optionalUserAction.async { implicit accountOpt => implicit request =>
    Feedback.form.bindFromRequest.fold(
      errorForm => immediate(BadRequest(errorForm.errorsAsJson)),
      feedback => {
        val moreFeedback = accountOpt.map { account =>
          feedback
            .copy(name = feedback.name.orElse(Some(account.id)))
            .copy(email = feedback.email.orElse(Some(account.email)))
        }.getOrElse(feedback)
          .copy(context = Some(FeedbackContext.fromRequest))

        feedbackDAO.create(moreFeedback).map { id =>
          Ok(id)
        }
      }
    )
  }
}
