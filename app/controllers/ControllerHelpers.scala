package controllers

import scala.concurrent.Future
import play.api.mvc.RequestHeader
import models.sql.User
import play.api.mvc.Result
import play.api.libs.concurrent.execution.defaultContext
import rest._
import play.api.mvc.Controller

trait ControllerHelpers {
  this: Controller with AuthController =>
  /**
   * Wrapper function which takes a promise, and if it fails
   * examines the error for REST errors we know how to handle.
   */
  def WrapRest(promise: Future[Result])(implicit maybeUser: Option[User], request: RequestHeader): Future[Result] = {
    promise.recover {
      case err @ PermissionDenied => maybeUser match {
        case Some(user) => Unauthorized(views.html.errors.permissionDenied())
        case None => authenticationFailed(request)
      }
      case ItemNotFound => NotFound(views.html.errors.itemNotFound())
      case err @ ValidationError => BadRequest(err.toString())
      case err @ _ => BadRequest(err.toString())
    }
  }
}