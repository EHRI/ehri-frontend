package controllers.base

import scala.concurrent.Future
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.libs.concurrent.execution.defaultContext
import rest._
import play.api.mvc.Controller
import play.api.mvc.AsyncResult

trait ControllerHelpers {
  this: Controller with AuthController =>

   /**
   * Wrapper function which takes a promise of either a result
   * or a throwable. If the throwable exists it is handled in
   * an appropriate manner and returned as a AsyncResult
   */
  def AsyncRest(promise: Future[Either[Throwable, Result]])(implicit maybeUser: Option[User], request: RequestHeader): AsyncResult = {
    Async {
      promise.map { respOrErr =>
        respOrErr.fold(
          err => err match {
            case err @ PermissionDenied => maybeUser match {
              case Some(user) => Unauthorized(views.html.errors.permissionDenied())
              case None => authenticationFailed(request)
            }
            case ItemNotFound => NotFound(views.html.errors.itemNotFound())
            case err @ ValidationError => BadRequest(err.toString())
            case err @ _ => BadRequest(err.toString())
          },
          resp => resp
        )
      }
    }
  }
}