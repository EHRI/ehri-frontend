package controllers.api.graphql

import org.apache.pekko.util.ByteString
import controllers.AppComponents
import controllers.portal.base.PortalController
import play.api.http.{ContentTypes, HeaderNames, HttpVerbs}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents, RawBuffer}
import services.ServiceConfig
import services.data.Constants

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration


@Singleton
case class GraphQL @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ws: WSClient
) extends PortalController {

  val defaultQuery: String =
    """
      |{
      |  Country(id: "us") {
      |    name
      |    situation
      |  }
      |}
    """.stripMargin

  def index(): Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.api.graphql.docs(defaultQuery))
  }

  def graphiql: Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.api.graphql.graphiql(
      controllers.api.graphql.routes.GraphQL.query()))
  }

  def query: Action[RawBuffer] = OptionalUserAction.async(parsers.raw) { implicit request =>
    val bytes = request.body.asBytes().getOrElse(ByteString.empty).toArray
    val ct = request.contentType.getOrElse(ContentTypes.BINARY)
    val streamHeader: Option[String] = request.headers.get(Constants.STREAM_HEADER_NAME)
    val serviceConfig = ServiceConfig("ehridata", config)
    ws.url(s"${serviceConfig.baseUrl}/graphql")
      .withMethod(HttpVerbs.POST)
      .withRequestTimeout(config.get[Duration]("ehri.backend.streamingTimeout"))
      .addHttpHeaders(serviceConfig.authHeaders: _*)
      .addHttpHeaders(streamHeader.map(Constants.STREAM_HEADER_NAME -> _).toSeq: _*)
      .addHttpHeaders(request.userOpt.map(u => Constants.AUTH_HEADER_NAME -> u.id).toSeq: _*)
      .addHttpHeaders(HeaderNames.CONTENT_TYPE -> ct)
      .withBody(bytes)
      .stream().map { r =>
      Status(r.status).chunked(r.bodyAsSource).as(ContentTypes.JSON)
    }
  }
}
