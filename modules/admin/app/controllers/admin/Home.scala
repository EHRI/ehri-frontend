package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import models.{Isaar, IsadG}
import models.base.AnyModel
import controllers.generic.Search
import play.api._
import play.api.mvc._
import defines.EntityType
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.Json
import utils.search._
import solr.facet.FieldFacetClass

import com.google.inject._
import play.api.http.MimeTypes
import scala.concurrent.Future.{successful => immediate}
import backend.Backend

@Singleton
case class Home @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, backend: Backend) extends Search {

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

  private val entityFacets: FacetBuilder = { implicit lang =>
    List(

      FieldFacetClass(
        key=IsadG.LANG_CODE,
        name=Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
        param="lang",
        render= (s: String) => Helpers.languageCodeToName(s)
      ),

      // Holding repository
      FieldFacetClass(
        key="repositoryName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder"
      ),

      // Repositories by country
      FieldFacetClass(
        key="countryCode",
        name=Messages("isdiah.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s)
      ),

      // Historical agent type
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages(Isaar.FIELD_PREFIX + "." + s)
      )
    )
  }


  def index = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.index(Messages("pages.home.title")))
  }

  def profile = userProfileAction.async { implicit userOpt => implicit request =>
    userOpt.map { user =>
      immediate(Ok(views.html.profile(user)))
    } getOrElse {
      authenticationFailed(request)
    }
  }

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def overview = searchAction[AnyModel](
      defaultParams = Some(SearchParams(limit=Some(0))),
      entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    render {
      case Accepts.Json() => {
        Ok(Json.toJson(Json.obj(
          "facets" -> facets
        ))
        )
      }
      case _ => Ok("hello")
    }
  }

  def jsRoutes = Action { implicit request =>
    Ok(
      play.api.Routes.javascriptRouter("jsRoutes")(
        controllers.admin.routes.javascript.Metrics.languageOfMaterial,
        controllers.admin.routes.javascript.Metrics.holdingRepository,
        controllers.admin.routes.javascript.Metrics.repositoryCountries,
        controllers.admin.routes.javascript.Metrics.agentTypes,
        controllers.admin.routes.javascript.Metrics.restricted
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

}
