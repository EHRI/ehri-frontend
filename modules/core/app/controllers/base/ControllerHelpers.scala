package controllers.base

import scala.concurrent.Future
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import rest._
import play.api.mvc.Controller
import play.api.mvc.AsyncResult
import java.net.ConnectException
import models.UserProfile
import play.api.Play.current
import play.api.libs.json.Json

object ControllerHelpers {
  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH")
      .map(_.toUpperCase() == "XMLHTTPREQUEST").getOrElse(false)
}

trait ControllerHelpers {
  this: Controller with AuthController =>

  /**
   * Ensure that an action is performed by a logged-in user. This can be globally
   * disabled by setting ehri.secured = false in the application.conf.
   * @param res
   * @param userOpt
   * @param request
   * @return
   */
  def Secured(res: Result)(implicit userOpt: Option[models.UserProfile], request: RequestHeader): Result = {
    if (current.configuration.getBoolean("ehri.secured").getOrElse(true))
      if (userOpt.isDefined) res else authenticationFailed(request)
    else
      res
  }

  /**
   * Get a complete list of possible groups
   * @param f
   * @param userOpt
   * @param request
   * @return
   */
  def getGroups(f: Seq[(String,String)] => Result)(implicit userOpt: Option[UserProfile], request: RequestHeader) = {
    // TODO: Handle REST errors
    Async {
      for {
        groups <- rest.RestHelpers.getGroupList
      } yield {
        f(groups)
      }
    }
  }

  /**
   * Join params into a query string
   */
  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))
    }}.flatten.mkString("&")
  }


  /**
   * Wrapper function which takes a promise of either a result
   * or a throwable. If the throwable exists it is handled in
   * an appropriate manner and returned as a AsyncResult
   */
  def AsyncRest(promise: Future[Either[Throwable, Result]])(implicit maybeUser: Option[UserProfile], request: RequestHeader): AsyncResult = {
    Async {
      promise.map { respOrErr =>
        respOrErr.fold(
          err => err match {
            // TODO: Rethink whether we want to redirect here?  All our
            // actions should already be permission-secure, so it's really
            // an error if the server denies permission for something.
            case e: PermissionDenied => maybeUser.map { user =>
              Unauthorized(views.html.errors.permissionDenied(Some(e)))
            } getOrElse {
              render {
                case Accepts.Json() => println(e); Unauthorized(Json.toJson(e))
                case _ => authenticationFailed(request)
              }
            }
            case e: ItemNotFound => NotFound(views.html.errors.itemNotFound())
            case e: ValidationError => BadRequest(err.toString())
            case e: ServerError => InternalServerError(views.html.errors.serverTimeout())
            case e => BadRequest(e.toString())
          },
          resp => resp
        )
      } recover {
        case e: ConnectException => InternalServerError(views.html.errors.serverTimeout())
      }
    }
  }
}