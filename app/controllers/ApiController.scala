package controllers

import controllers.base.AuthController
import play.api.mvc.{Action, Controller}
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

  def getAny(id: String) = Action { implicit request =>
    get(s"entities?id=$id")(request)
  }

  def get(urlpart: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        val url = urlpart + (if(request.rawQueryString.trim.isEmpty) "" else "?" + request.rawQueryString)
        rest.ApiDAO(maybeUser)
          .get(url, request.headers).map { r =>
            Status(r.status)
              .stream(Enumerator.fromStream(r.ahcResponse.getResponseBodyAsStream))
              .as(r.ahcResponse.getContentType)
          }
      }
  }
}