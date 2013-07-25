package controllers.core

import controllers.base.AuthController
import play.api.mvc.{Action, Controller}
import controllers.base.ControllerHelpers
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits._

import com.google.inject._
import global.GlobalConfig

class ApiController @Inject()(val globalConfig: GlobalConfig) extends Controller with AuthController with ControllerHelpers {

  def listItems(contentType: String) = Action { implicit request =>
    get(s"$contentType/list")(request)
  }

  def getItem(contentType: String, id: String) = Action { implicit request =>
    get(s"$contentType/$id")(request)
  }

  def getAny(id: String) = Action { implicit request =>
    get(s"entities?id=$id")(request)
  }

  def get(urlpart: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      Async {
        val url = urlpart + (if(request.rawQueryString.trim.isEmpty) "" else "?" + request.rawQueryString)
        rest.ApiDAO(maybeUser)
          .get(url, request.headers).map { r =>
            Status(r.status)
              .stream(Enumerator.fromStream(r.ahcResponse.getResponseBodyAsStream))
              .as(r.ahcResponse.getContentType)
          }
      }
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

  def sparqlQuery = userProfileAction { implicit userOpt => implicit request =>
    Async {
      rest.ApiDAO(userOpt).get("sparql", request.queryString, request.headers).map { r =>
        Status(r.status)
          .stream(Enumerator.fromStream(r.ahcResponse.getResponseBodyAsStream))
          .as(r.ahcResponse.getContentType)
      }
    }
  }
}