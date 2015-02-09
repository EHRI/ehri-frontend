package controllers.base

import play.api.Logger
import play.api.Play._
import play.api.mvc.{Result, RequestHeader}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.Future.{successful => immediate}
import controllers.renderError
import views.html.errors.{maintenance, itemNotFound}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait AdminController extends AuthController with ControllerHelpers with AuthConfigImpl {

  def pageRelocator: utils.MovedPageLookup

  override def verifiedOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.verifiedOnly", views.html.errors.verifiedOnly())))
  }

  override def staffOnlyError(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(Unauthorized(renderError("errors.staffOnly", views.html.errors.staffOnly())))
  }

  override def notFoundError(request: RequestHeader, msg: Option[String] = None)(implicit context: ExecutionContext): Future[Result] = {
    val doMoveCheck: Boolean = current.configuration.getBoolean("ehri.handlePageMoved").getOrElse(false)
    implicit val r  = request
    val notFoundResponse = NotFound(renderError("errors.itemNotFound", itemNotFound(msg)))
    if (!doMoveCheck) immediate(notFoundResponse)
    else for {
      maybeMoved <- pageRelocator.hasMovedTo(request.path)
    } yield maybeMoved match {
        case Some(path) => MovedPermanently(path)
        case None => notFoundResponse
      }
  }

  override def downForMaintenance(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    implicit val r  = request
    immediate(ServiceUnavailable(renderError("errors.maintenance", maintenance())))
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
