package controllers.api.oaipmh

import akka.util.ByteString
import config.{serviceAuthHeaders, serviceBaseUrl}
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
    val name = "ehridata"
    ws.url(s"${serviceBaseUrl(name, config)}/oaipmh?" + request.rawQueryString)
      .withHttpHeaders(serviceAuthHeaders(name, config): _*)
      .withMethod(HttpVerbs.GET)
      .withBody(bytes)
      .stream().map { r =>
      Status(r.status).chunked(r.bodyAsSource).as(ContentTypes.XML)
    }
  }
}
