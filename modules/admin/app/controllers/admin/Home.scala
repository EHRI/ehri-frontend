package controllers.admin

import auth.AccountManager
import play.api.cache.CacheApi
import models.{Isaar, SystemEvent}
import models.base.{AnyModel, Description}
import controllers.generic.Search
import play.api.mvc._
import defines.{EntityType, EventType}
import play.api.i18n.{Messages, MessagesApi}
import views.Helpers
import play.api.libs.json.Json
import utils.search._
import javax.inject._

import auth.handler.AuthHandler
import play.api.http.MimeTypes

import scala.concurrent.Future.{successful => immediate}
import backend.DataApi
import utils.{MovedPageLookup, RangeParams, SystemEventParams}
import controllers.base.AdminController

import scala.concurrent.ExecutionContext


@Singleton
case class Home @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,
  executionContext: ExecutionContext,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi
) extends AdminController
  with Search {

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

  private val entityFacets: FacetBuilder = { implicit request =>
    List(

      FieldFacetClass(
        key=Description.LANG_CODE,
        name=Messages("documentaryUnit." + Description.LANG_CODE),
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


  def index = OptionalUserAction.async { implicit request =>
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

    request.userOpt.map { user =>
      val listParams = RangeParams.fromRequest(request)
      val eventFilter = SystemEventParams.fromRequest(request)
        .copy(eventTypes = activityEventTypes)
        .copy(itemTypes = activityItemTypes)
      userDataApi.userEvents[SystemEvent](user.id, listParams, eventFilter).map { events =>
        Ok(views.html.admin.index(Some(events)))
      }
    } getOrElse {
      immediate(Ok(views.html.admin.index(None)))
    }
  }

  def metrics = OptionalUserAction { implicit request =>
    Ok(views.html.admin.metrics())
  }

  def loginRedirect() = Action {
    MovedPermanently(controllers.portal.account.routes.Accounts.loginOrSignup().url)
  }

  // NB: This page now just handles metrics and only provides facet
  // data via JSON.
  def overview = OptionalUserAction.async { implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(count=Some(0)),
      facetBuilder = entityFacets
    ).map { result =>
        render {
          case Accepts.Json() => Ok(Json.toJson(Json.obj("facets" -> result.facetClasses)))
          case _ => MovedPermanently(controllers.admin.routes.Home.metrics().url)
        }
    }
  }

  def metricsJsRoutes = Action { implicit request =>
    Ok(
      play.api.routing.JavaScriptReverseRouter("metricsJsRoutes")(
        controllers.admin.routes.javascript.Metrics.languageOfMaterial,
        controllers.admin.routes.javascript.Metrics.holdingRepository,
        controllers.admin.routes.javascript.Metrics.repositoryCountries,
        controllers.admin.routes.javascript.Metrics.agentTypes,
        controllers.admin.routes.javascript.Metrics.restricted
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

}
