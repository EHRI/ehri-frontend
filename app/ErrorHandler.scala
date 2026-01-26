import auth.handler.AuthHandler
import views.AppConfig
import controllers.api.v1.ApiV1
import controllers.renderError
import cookies.{SessionPreferences, SessionPrefs}

import javax.inject.{Inject, Provider}
import models.UserProfile
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n._
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import play.api.{Configuration, Environment, OptionalSourceMapper, UsefulException}
import services.ServiceOffline
import services.data._
import services.redirects.MovedPageLookup
import views.html.errors._

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


class ErrorHandler @Inject() (
  env: Environment,
  config: Configuration,
  sourceMapper: OptionalSourceMapper,
  pageRelocator: MovedPageLookup,
  router: Provider[Router],
  langs: Langs,
  authHandler: AuthHandler,
  dataApi: DataServiceBuilder
)(implicit val messagesApi: MessagesApi, conf: AppConfig, executionContext: ExecutionContext)
  extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
with I18nSupport
with SessionPreferences[SessionPrefs] {

  override val defaultPreferences = new SessionPrefs

  private def userContext(request: RequestHeader): Future[Option[UserProfile]] = {
    authHandler.restoreAccount(request).flatMap {
      case (Some(account), _) => dataApi
        .withContext(AuthenticatedUser(account.id))
        .fetch[UserProfile](Seq(account.id))
        .map(_.headOption.flatten)
        .recover {
          // Ensure we don't throw another exception which leads to
          // another invocation of this very error handler...
          case _:Exception => Option.empty
      }
      case _ => immediate(Option.empty)
    }
  }

  // NB: Handling this *also* overrides onNotFound
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    implicit val r: RequestHeader = request
    userContext(request).flatMap { implicit userOpt =>
      statusCode match {
        case play.api.http.Status.NOT_FOUND => onNotFound(request, message)
        case _ if request.path.startsWith(controllers.api.v1.routes.ApiV1Home.index().url) =>
          immediate(Status(statusCode)(ApiV1.errorJson(statusCode, Some(message))))
        case _ =>
          immediate(Status(statusCode)(renderError("errors.clientError", genericError(message))))
      }
    }
  }

  override def onNotFound(request: RequestHeader, message: String): Future[Result] = {
    implicit val r: RequestHeader = request
    userContext(request).flatMap { implicit userOpt =>
      // if the page has a trailing slash, permanently redirect without the slash...
      if (request.path.endsWith("/")) {
        immediate(MovedPermanently(request.path.dropRight(1)))
      } else pageRelocator.hasMovedTo(request.path).map {
        case Some(newPath) => MovedPermanently(utils.http.iriToUri(newPath))
        case None => NotFound(renderError("errors.pageNotFound", pageNotFound()))
      }
    }
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val r: RequestHeader = request
    userContext(request).flatMap { implicit userOpt =>
      exception match {
        case e: PermissionDenied => immediate(Unauthorized(
          renderError("errors.permissionDenied", permissionDenied(Some(e)))))
        case ItemNotFound(_, Some(id), _, Some(since)) =>
          immediate(Gone(
            renderError("errors.gone", gone(id, since))))
        case e: ItemNotFound =>
          immediate(NotFound(
            renderError("errors.itemNotFound", itemNotFound(e.value))))
        case _: services.search.SearchEngineOffline => immediate(InternalServerError(
          renderError("errors.searchEngineError", searchEngineError())))
        case _: ServiceOffline => immediate(InternalServerError(
          renderError("errors.databaseError", serverTimeout())))

        // Other unhandled errors, propagate to the application error handler.
        case _ => super.onServerError(request, exception)
      }
    }
  }

  override def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
    implicit val r: RequestHeader = request
    userContext(request).map { implicit userOpt =>
        InternalServerError(renderError("errors.genericProblem", fatalError()))
    }
  }

  override def onForbidden(request: RequestHeader, message: String): Future[Result] = {
    implicit val r: RequestHeader = request
    userContext(request).map { implicit userOpt =>
      Forbidden(renderError("errors.permissionDenied", permissionDenied()))
    }
  }
}
