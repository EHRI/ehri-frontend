package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import models.{AccountDAO, Isaar, IsadG}
import models.base.AnyModel
import controllers.generic.Search
import play.api._
import play.api.mvc._
import defines.{EventType, EntityType}
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.Json
import utils.search._
import solr.facet.FieldFacetClass

import com.google.inject._
import play.api.http.MimeTypes
import scala.concurrent.Future.{successful => immediate}
import backend.Backend
import utils.{SystemEventParams, ListParams}


@Singleton
case class Home @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Search {

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

  private val entityFacets: FacetBuilder = { implicit request =>
    List(

      FieldFacetClass(
        key=IsadG.LANG_CODE,
        name=Messages("documentaryUnit." + IsadG.LANG_CODE),
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
        name=Messages("repository.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s)
      ),

      // Historical agent type
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages("historicalAgent." + s)
      )
    )
  }


  def index = userProfileAction.async { implicit userOpt => implicit request =>
    val activityEventTypes = List(
      EventType.deletion,
      EventType.creation,
      EventType.modification,
      EventType.modifyDependent,
      EventType.createDependent,
      EventType.deleteDependent,
      EventType.link,
      EventType.annotation
    )

    val activityItemTypes = List(
      EntityType.DocumentaryUnit,
      EntityType.Repository,
      EntityType.Country,
      EntityType.HistoricalAgent
    )

    userOpt.map { user =>
      val listParams = ListParams.fromRequest(request)
      val eventFilter = SystemEventParams.fromRequest(request)
        .copy(eventTypes = activityEventTypes)
        .copy(itemTypes = activityItemTypes)
      backend.listEventsForUser(user.id, listParams, eventFilter).map { events =>
        Ok(views.html.index(Some(events)))
      }
    } getOrElse {
      immediate(Ok(views.html.index(None)))
    }
  }

  def metrics = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.metrics())
  }

  def loginRedirect() = Action {
    MovedPermanently(controllers.portal.routes.Profile.login().url)
  }

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def overview = searchAction[AnyModel](
      defaultParams = SearchParams(limit=Some(0)),
      entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    render {
      case Accepts.Json() => {
        Ok(Json.toJson(Json.obj(
          "facets" -> facets
        ))
        )
      }
      case _ => MovedPermanently(controllers.admin.routes.Home.metrics().url)
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
