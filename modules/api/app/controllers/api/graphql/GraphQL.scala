package controllers.api.graphql

import javax.inject.{Inject, Singleton}

import akka.util.ByteString
import auth.AccountManager
import backend.DataApi
import backend.rest.Constants
import controllers.portal.base.PortalController
import play.api.cache.CacheApi
import play.api.http.{ContentTypes, HeaderNames}
import play.api.i18n.MessagesApi
import play.api.mvc.BodyParsers
import utils.MovedPageLookup
import play.api.libs.ws.WSClient
import views.MarkdownRenderer

import scala.concurrent.ExecutionContext


@Singleton
case class GraphQL @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  ws: WSClient,
  executionContext: ExecutionContext
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

    ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/graphql")
        .withHeaders(request.userOpt.map(u => Constants.AUTH_HEADER_NAME -> u.id).toSeq: _*)
        .withHeaders(HeaderNames.CONTENT_TYPE -> ct)
        .post(bytes).map { r =>
      Status(r.status)(r.bodyAsBytes).as(ContentTypes.JSON)
    }
  }
}