import javax.inject.{Inject, Provider}

import backend.rest.{BadJson, ItemNotFound, PermissionDenied}
import global.{AppGlobalConfig, GlobalConfig}
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n.{MessagesApi, I18nSupport, DefaultLangs, DefaultMessagesApi}
import play.api.mvc._
import play.api.mvc.Results._
import play.api.routing.Router

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

import controllers.renderError
import views.html.errors.{permissionDenied, itemNotFound, serverTimeout, fatalError}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class ErrorHandler @Inject() (
  env: Environment,
  config: Configuration,
  sourceMapper: OptionalSourceMapper,
  router: Provider[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) with I18nSupport {

  implicit def messagesApi: MessagesApi = new DefaultMessagesApi(env, config, new DefaultLangs(config))
  implicit def globalConfig: GlobalConfig = new AppGlobalConfig(config)

  override def onNotFound(request: RequestHeader, message: String): Future[Result] = {
    implicit val r = request
    immediate(NotFound(renderError("errors.pageNotFound", views.html.errors.pageNotFound())))
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