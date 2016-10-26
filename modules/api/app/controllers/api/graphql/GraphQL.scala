package controllers.api.graphql

import javax.inject.{Inject, Singleton}

import akka.util.ByteString
import backend.rest.Constants
import controllers.Components
import controllers.portal.base.PortalController
import play.api.http.{ContentTypes, HeaderNames, HttpVerbs}
import play.api.libs.ws.WSClient
import play.api.mvc.BodyParsers


@Singleton
case class GraphQL @Inject()(
  components: Components,
  ws: WSClient
) extends PortalController {

  val defaultQuery =
    """
      |{
      |  Country(id: "us") {
      |    name
      |    situation
      |  }
      |}
    """.stripMargin

  def index() = OptionalUserAction { implicit request =>
    Ok(views.html.api.graphql.docs(defaultQuery))
  }

  def graphiql = OptionalUserAction { implicit request =>
    Ok(views.html.api.graphql.graphiql(
      controllers.api.graphql.routes.GraphQL.query()))
  }

  def query = OptionalUserAction.async(BodyParsers.parse.raw) { implicit request =>
    val bytes = request.body.asBytes().getOrElse(ByteString.empty).toArray
    val ct = request.contentType.getOrElse(ContentTypes.BINARY)
    val streamHeader: Option[String] = request.headers.get(Constants.STREAM_HEADER_NAME)
    ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/graphql")
        .withHeaders(streamHeader.map(Constants.STREAM_HEADER_NAME -> _).toSeq: _*)
        .withHeaders(request.userOpt.map(u => Constants.AUTH_HEADER_NAME -> u.id).toSeq: _*)
        .withHeaders(HeaderNames.CONTENT_TYPE -> ct)
        .withMethod(HttpVerbs.POST)
        .withBody(bytes)
        .stream().map { r =>
      Status(r.headers.status).chunked(r.body).as(ContentTypes.JSON)
    }
  }
}