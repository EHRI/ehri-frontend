package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import controllers.base.{AuthController, ControllerHelpers}
import models.{FeedbackContext, Feedback}
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import play.api.Play.current
import play.api.libs.ws.WS
import play.api.libs.json.Json
import play.api.Logger

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalFeedback {
  self: Controller with ControllerHelpers with AuthController =>

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

        saveFeedback(moreFeedback).map { ok =>
          Ok(ok.toString)
        }
      }
    )
  }

  private def saveFeedback(feedback: Feedback): Future[Boolean] = {
    val appKey = current.configuration.getString("parse.keys.applicationId").getOrElse("fake")
    val restKey = current.configuration.getString("parse.keys.restApiKey").getOrElse("fake")
    val url = current.configuration.getString("parse.feedbackUrl").getOrElse("fake")
    val headers = Seq(
      "X-Parse-Application-Id" -> appKey,
      "X-Parse-REST-API-Key" -> restKey,
      "Content-type" -> "application/json"
    )
    WS.url(url).withHeaders(headers: _*).post(Json.toJson(feedback)).map { response =>
      Logger.info("Parse response: " + response.body)
      response.status >= 200 && response.status < 300
    }
  }
}
