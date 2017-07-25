import javax.inject.{Inject, Provider}

import services.rest.{BadJson, ItemNotFound, PermissionDenied}
import controllers.base.SessionPreferences
import global.GlobalConfig
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n._
import play.api.mvc.Results._
import play.api.routing.Router
import utils.SessionPrefs

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import controllers.renderError
import play.api.{Configuration, Environment, OptionalSourceMapper, UsefulException}
import play.api.mvc.{RequestHeader, Result}
import views.html.errors._

class ErrorHandler @Inject() (
  env: Environment,
  config: Configuration,
  sourceMapper: OptionalSourceMapper,
  router: Provider[Router],
  langs: Langs
)(implicit val messagesApi: MessagesApi, globalConfig: GlobalConfig)
  extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
with I18nSupport
with SessionPreferences[SessionPrefs] {

  override val defaultPreferences = new SessionPrefs

  // NB: Handling this *also* overrides onNotFound
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    implicit val r = request

    statusCode match {
      case play.api.http.Status.NOT_FOUND => onNotFound(request, message)
      case _ => immediate(
        Status(statusCode)(
          renderError("errors.clientError", genericError(message)))
      )
    }
  }

  override def onNotFound(request: RequestHeader, message: String): Future[Result] = {
    implicit val r = request
    immediate(NotFound(renderError("errors.pageNotFound", pageNotFound())))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val r = request

    exception match {
      case e: PermissionDenied => immediate(Unauthorized(
        renderError("errors.permissionDenied", permissionDenied(Some(e)))))
      case e: ItemNotFound => immediate(NotFound(
        renderError("errors.itemNotFound", itemNotFound(e.value))))
      case e: utils.search.SearchEngineOffline => immediate(InternalServerError(
        renderError("errors.searchEngineError", searchEngineError())))
      case e: services.rest.ServiceOffline => immediate(InternalServerError(
        renderError("errors.databaseError", serverTimeout())))
      case e: BadJson => sys.error(e.getMessageWithContext(request))

      case e => super.onServerError(request, exception)
    }
  }

  override def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
    implicit val r = request
    immediate(InternalServerError(
      renderError("errors.genericProblem", fatalError())))
  }

  override def onForbidden(request: RequestHeader, message: String): Future[Result] = {
    implicit val r = request
    immediate(
      Forbidden(renderError("errors.permissionDenied", permissionDenied()))
    )
  }
}