import javax.inject.{Inject, Provider}

import backend.rest.{BadJson, ItemNotFound, PermissionDenied}
import controllers.base.SessionPreferences
import global.{AppGlobalConfig, GlobalConfig}
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.routing.Router
import utils.SessionPrefs

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

import controllers.renderError
import views.html.errors.{permissionDenied, itemNotFound, serverTimeout, fatalError, pageNotFound, genericError}

class ErrorHandler @Inject() (
  env: Environment,
  config: Configuration,
  sourceMapper: OptionalSourceMapper,
  router: Provider[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
with I18nSupport
with SessionPreferences[SessionPrefs] {

  override val defaultPreferences = new SessionPrefs

  implicit def messagesApi: MessagesApi = new DefaultMessagesApi(env, config, new DefaultLangs(config))
  implicit def globalConfig: GlobalConfig = new AppGlobalConfig(config)

  override implicit def request2Messages(implicit request: RequestHeader): Messages = {
    request.preferences.language match {
      case None => super.request2Messages(request)
      case Some(lang) => super.request2Messages(request).copy(lang = Lang(lang))
    }
  }

  // NB: Handling this *also* overrides onNotFound
  override def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
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
      case e: java.net.ConnectException => immediate(InternalServerError(
        renderError("errors.databaseError", serverTimeout())))
      case e: BadJson => sys.error(e.getMessageWithContext(request))

      case e => super.onServerError(request, exception)
    }
  }

  override def onProdServerError(request: RequestHeader, exception: UsefulException) = {
    implicit val r = request
    immediate(InternalServerError(
      renderError("errors.genericProblem", fatalError())))
  }

  override def onForbidden(request: RequestHeader, message: String) = {
    implicit val r = request
    immediate(
      Forbidden(renderError("errors.permissionDenied", permissionDenied()))
    )
  }
}