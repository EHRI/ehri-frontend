package controllers.admin

import controllers.base.EntitySearch
import models.{Isaar, IsadG}
import models.base.AnyModel

import play.api._
import play.api.mvc._
import defines.EntityType
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.{Writes, Json}
import utils.search._
import solr.facet.FieldFacetClass

import com.google.inject._
import play.api.http.MimeTypes


@Singleton
class Home @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends EntitySearch {

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

  private val entityFacets = List(

    FieldFacetClass(
      key=IsadG.LANG_CODE,
      name=Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
      param="lang",
      render=Helpers.languageCodeToName
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
      render=Helpers.countryCodeToName
    ),

    // Historical agent type
    FieldFacetClass(
      key=models.Isaar.ENTITY_TYPE,
      name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
      param="cpf",
      render=s => Messages(Isaar.FIELD_PREFIX + "." + s)
    )
  )


  def index = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      Ok(views.html.index(Messages("pages.home.title")))
    }
  }

  def profile = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      userOpt.map { user =>
        Ok(views.html.profile(user))
      } getOrElse {
        authenticationFailed(request)
      }
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
