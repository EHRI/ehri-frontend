import controllers.api.v1.ApiV1
import controllers.base.SessionPreferences
import controllers.renderError
import global.GlobalConfig
import javax.inject.{Inject, Provider}
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n._
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import play.api.{Configuration, Environment, OptionalSourceMapper, UsefulException}
import services.ServiceOffline
import services.data.{BadJson, ItemNotFound, PermissionDenied}
import services.redirects.MovedPageLookup
import utils.SessionPrefs
import views.html.errors._

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


class ErrorHandler @Inject() (
  env: Environment,
  config: Configuration,
  sourceMapper: OptionalSourceMapper,
  pageRelocator: MovedPageLookup,
  router: Provider[Router],
  langs: Langs
)(implicit val messagesApi: MessagesApi, globalConfig: GlobalConfig, executionContext: ExecutionContext)
  extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
with I18nSupport
with SessionPreferences[SessionPrefs] {

  override val defaultPreferences = new SessionPrefs

  // NB: Handling this *also* overrides onNotFound
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    implicit val r: RequestHeader = request

    statusCode match {
      case play.api.http.Status.NOT_FOUND => onNotFound(request, message)
      case _ if request.path.startsWith(controllers.api.v1.routes.ApiV1Home.index().url) =>
        immediate(Status(statusCode)(ApiV1.errorJson(statusCode, Some(message))))
      case _ =>
        immediate(Status(statusCode)(renderError("errors.clientError", genericError(message))))
    }
  }

  override def onNotFound(request: RequestHeader, message: String): Future[Result] = {
    implicit val r: RequestHeader = request
    // if the page has a trailing slash, permanently redirect without the slash...
    if (request.path.endsWith("/")) {
      immediate(MovedPermanently(request.path.dropRight(1)))
    } else pageRelocator.hasMovedTo(request.path).map {
      case Some(newPath) => MovedPermanently(utils.http.iriToUri(newPath))
      case None => NotFound(renderError("errors.pageNotFound", pageNotFound()))
    }
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val r: RequestHeader = request

    exception match {
      case e: PermissionDenied => immediate(Unauthorized(
        renderError("errors.permissionDenied", permissionDenied(Some(e)))))
      case e: ItemNotFound => immediate(NotFound(
        renderError("errors.itemNotFound", itemNotFound(e.value))))
      case e: services.search.SearchEngineOffline => immediate(InternalServerError(
        renderError("errors.searchEngineError", searchEngineError())))
      case e: ServiceOffline => immediate(InternalServerError(
        renderError("errors.databaseError", serverTimeout())))
      case e => super.onServerError(request, exception)
    }
  }

  override def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
    implicit val r: RequestHeader = request
    immediate(
      InternalServerError(renderError("errors.genericProblem", fatalError())))
  }

  override def onForbidden(request: RequestHeader, message: String): Future[Result] = {
    implicit val r: RequestHeader = request
    immediate(
      Forbidden(renderError("errors.permissionDenied", permissionDenied())))
  }
}