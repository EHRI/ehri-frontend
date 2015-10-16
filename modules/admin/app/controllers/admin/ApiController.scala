package controllers.admin

import auth.AccountManager
import controllers.base.AdminController
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.ws.WSClient
import play.api.mvc.Action
import play.api.libs.concurrent.Execution.Implicits._

import javax.inject._
import backend.Backend
import play.api.http.HeaderNames
import defines.EntityType
import backend.rest.cypher.Cypher
import utils.MovedPageLookup
import views.MarkdownRenderer

case class ApiController @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher,
  ws: WSClient
) extends AdminController {

  private def passThroughHeaders(headers: Map[String, Seq[String]],
                                 filter: Seq[String] = Seq.empty): Seq[(String, String)] = {
    headers.filter(kv => if (filter.isEmpty) true else filter.contains(kv._1)).flatMap { case (k, seq) =>
      seq.map(s => k -> s)
    }.toSeq
  }

  def getItem(contentType: EntityType.Value, id: String) = Action.async { implicit request =>
    get(s"$contentType/$id")(request)
  }

  def get(urlPart: String) = OptionalUserAction.async { implicit request =>
    val url = urlPart + (if(request.rawQueryString.trim.isEmpty) "" else "?" + request.rawQueryString)
    userBackend.stream(url, request.headers).map { case (headers, stream) =>
      val length = headers.headers
        .get(HeaderNames.CONTENT_LENGTH).flatMap(_.headOption.map(_.toInt)).getOrElse(-1)
      val result:Status = Status(headers.status)
      val rHeaders = passThroughHeaders(headers.headers)
      if (length > 0) result.stream(stream).withHeaders(rHeaders: _*)
      else result.chunked(stream).withHeaders(rHeaders: _*)
    }
  }

  //
  // Test guff for the Sparql endpoint...
  //

  import play.api.data.Form
  import play.api.data.Forms._
  private val queryForm = Form(single("q" -> text))

  private val defaultCypher =
    """
      |START n = node:entities("__ISA__:userProfile")
      |RETURN n, n.name
      |LIMIT 100
    """.stripMargin

  def cypherForm = AdminAction { implicit request =>
    Ok(views.html.admin.queryForm(queryForm.fill(defaultCypher),
      controllers.admin.routes.ApiController.cypherQuery(), "Cypher"))
  }

  def cypherQuery = AdminAction.async { implicit request =>
    cypher.stream(queryForm.bindFromRequest.value.getOrElse(""), Map.empty).map { case(headers, stream) =>
      Status(headers.status)
        .chunked(stream)
        .withHeaders(passThroughHeaders(headers.headers): _*)
    }
  }

  private val defaultSparql =
    """
      |PREFIX edge:   <http://tinkerpop.com/pgm/edge/>
      |PREFIX vertex: <http://tinkerpop.com/pgm/vertex/>
      |PREFIX prop:   <http://tinkerpop.com/pgm/property/>
      |PREFIX pgm:    <http://tinkerpop.com/pgm/ontology#>
      |PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      |
      |# Select all the userProfile nodes and their name properties...
      |SELECT ?n ?u WHERE {
      |    ?u a pgm:Vertex ;
      |       prop:__ISA__  "userProfile" ;
      |       prop:name     ?n .
      |}
      |
      |LIMIT 100
    """.stripMargin

  def sparql = AdminAction { implicit request =>
    Ok(views.html.admin.queryForm(queryForm.fill(defaultSparql),
        controllers.admin.routes.ApiController.sparqlQuery(), "SparQL"))
  }

  def sparqlQuery = AdminAction.async { implicit request =>
    userBackend.stream("sparql", request.headers, request.queryString).map { case (headers, stream) =>
      Status(headers.status)
        .chunked(stream)
        .withHeaders(passThroughHeaders(headers.headers): _*)
    }
  }
}