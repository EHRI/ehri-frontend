package controllers.core

import controllers.base.AuthController
import play.api.mvc.{Action, Controller}
import controllers.base.ControllerHelpers
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits._

import com.google.inject._
import backend.Backend
import play.api.Routes
import play.api.http.MimeTypes
import models.AccountDAO
import com.ning.http.client.{Response => NingResponse}
import defines.EntityType

case class ApiController @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AuthController with ControllerHelpers {

  def listItems(contentType: EntityType.Value) = Action.async { implicit request =>
    get(s"$contentType/list")(request)
  }

  def getItem(contentType: EntityType.Value, id: String) = Action.async { implicit request =>
    get(s"$contentType/$id")(request)
  }

  def getAny(id: String) = Action.async { implicit request =>
    get(s"entities?id=$id")(request)
  }

  def get(urlpart: String) = userProfileAction.async { implicit maybeUser =>
    implicit request =>
    val url = urlpart + (if(request.rawQueryString.trim.isEmpty) "" else "?" + request.rawQueryString)
    backend.query(url, request.headers).map { r =>
      val response: NingResponse = r.underlying[NingResponse]
      Status(r.status)
        .chunked(Enumerator.fromStream(response.getResponseBodyAsStream))
        .as(response.getContentType)
    }
  }

  def jsRoutes = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(

      )
    ).as(MimeTypes.JAVASCRIPT)
  }



  //
  // Test guff for the Sparql endpoint...
  //

  import play.api.data.Form
  import play.api.data.Forms._
  private val sparqlForm = Form(single("q" -> text))

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

  def sparql = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.sparqlForm(sparqlForm.fill(defaultSparql),
        controllers.core.routes.ApiController.sparqlQuery))
  }

  def sparqlQuery = userProfileAction.async { implicit userOpt => implicit request =>
    backend.query("sparql", request.headers, request.queryString).map { r =>
      val response: NingResponse = r.underlying[NingResponse]
      Status(r.status)
        .chunked(Enumerator.fromStream(response.getResponseBodyAsStream))
        .as(response.getContentType)
    }
  }
}