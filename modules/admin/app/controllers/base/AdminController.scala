package controllers.base

import play.api.Logger
import play.api.mvc.{Result, RequestHeader}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.Future.{successful => immediate}
import controllers.renderError
import views.html.errors.itemNotFound

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait AdminController extends AuthController with ControllerHelpers with AuthConfigImpl {

  def verifiedOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.verifiedOnly", views.html.errors.verifiedOnly())))
  }

  def staffOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.staffOnly", views.html.errors.staffOnly())))
  }

  def notFoundError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }

  /**
   * A redirect target after a failed authentication.
   */
  override def authenticationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    if (utils.isAjax(request)) {
      Logger.logger.warn("Auth failed for: {}", request.toString())
      immediate(Unauthorized("authentication failed"))
    } else {
      immediate(Redirect(controllers.portal.account.routes.Accounts.loginOrSignup())
        .withSession(ACCESS_URI -> request.uri))
    }
  }

  override def authorizationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r = request
    immediate(Forbidden(renderError("errors.permissionDenied", views.html.errors.permissionDenied())))
  }

  /**
   * Wrap some code generating an optional result, falling back to a 404.
   */
  def itemOr404(f: => Option[Result])(implicit request: RequestHeader): Result = {
    f.getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }

  /**
   * Given an optional item and a function to produce a
   * result from it, run the function or fall back on a 404.
   */
  def itemOr404[T](item: Option[T])(f: => T => Result)(implicit request: RequestHeader): Result = {
    item.map(f).getOrElse(NotFound(renderError("errors.itemNotFound", itemNotFound())))
  }
}
