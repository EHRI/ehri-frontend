package controllers.api.oaipmh

import akka.util.ByteString
import controllers.AppComponents
import controllers.portal.base.PortalController
import javax.inject.{Inject, Singleton}
import play.api.http.{ContentTypes, HttpVerbs}
import play.api.libs.ws.WSClient
import play.api.mvc._


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
    ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/oaipmh?" + request.rawQueryString)
      .withMethod(HttpVerbs.GET)
      .withBody(bytes)
      .stream().map { r =>
      Status(r.status).chunked(r.bodyAsSource).as(ContentTypes.XML)
    }
  }
}
