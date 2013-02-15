package controllers

import controllers.base.AuthController
import play.api.mvc.{Action, Controller, SimpleResult, ResponseHeader}
import controllers.base.ControllerHelpers
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Simple proxy controller that authorizes users and passes a request
 * directly on the the REST server. Currently only GET actions are
 * supported.
 */
object ApiController extends Controller with AuthController with ControllerHelpers {

  def listItems(contentType: String) = Action { implicit request =>
    get(s"$contentType/list")(request)
  }

  def getItem(contentType: String, id: String) = Action { implicit request =>
    get(s"$contentType/$id")(request)
  }

  def get(urlpart: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        rest.ApiDAO(maybeUser)
          .get(List(urlpart, request.rawQueryString).mkString("?"), request.headers).map { r =>
            SimpleResult(body = Enumerator(r.body), header = ResponseHeader(status = r.status))
          }
      }
  }
}