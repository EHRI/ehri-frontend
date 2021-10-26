package controllers.api.oaipmh

import akka.util.ByteString
import config.ServiceConfig
import controllers.AppComponents
import controllers.portal.base.PortalController
import play.api.http.{ContentTypes, HttpVerbs}
import play.api.libs.ws.WSClient
import play.api.mvc._

import javax.inject.{Inject, Singleton}


@Singleton
case class OaiPmhHome @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ws: WSClient
) extends PortalController {

  def index(): Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.api.oaipmh.docs())
  }

  /**
    * Proxy to the actual OAI-PMH service. This is mainly useful for
    * testing and in practice would be better done directly in the proxy.
    */
  def query: Action[RawBuffer] = OptionalUserAction.async(parsers.raw) { implicit request =>
    val bytes = request.body.asBytes().getOrElse(ByteString.empty).toArray
    val serviceConfig = ServiceConfig("ehridata", config)
    ws.url(s"${serviceConfig.baseUrl}/oaipmh?" + request.rawQueryString)
      .withHttpHeaders(serviceConfig.authHeaders: _*)
      .withMethod(HttpVerbs.GET)
      .withBody(bytes)
      .stream().map { r =>
      Status(r.status).chunked(r.bodyAsSource).as(ContentTypes.XML)
    }
  }
}
