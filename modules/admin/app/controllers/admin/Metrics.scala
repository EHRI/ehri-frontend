package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import models.{AccountDAO, Isaar, IsadG}
import models.base.AnyModel
import controllers.generic.Search
import play.api.Play.current
import play.api.mvc._
import defines.EntityType
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.Json
import utils.search._
import solr.facet.FieldFacetClass

import com.google.inject._
import play.api.cache.{Cache, Cached}
import backend.Backend
import models.json.ClientConvertable


@Singleton
case class Metrics @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Search {

  private val metricCacheTime = 60 * 60 // 1 hour

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

  private def jsonResponse[T](result: QueryResult[T])(implicit request: Request[AnyContent], w: ClientConvertable[T]): SimpleResult = {
    render {
      case Accepts.Json() | Accepts.JavaScript() => Ok(Json.obj(
        "page" -> Json.toJson(result.page.copy(items = result.page.items.map(_._1)))(ItemPage.itemPageWrites),
        "params" -> Json.toJson(result.params)(SearchParams.Converter.clientFormat),
        "appliedFacets" -> Json.toJson(result.facets)
      )).as(play.api.http.ContentTypes.JSON)
      case _ => UnsupportedMediaType
    }
  }

  // For all of the metrics we're just using facet counts,
  // so set the result limit to be zero.
  private val defaultParams = SearchParams(limit=Some(0))

  private val langCountFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key=IsadG.LANG_CODE,
        name=Messages("documentaryUnit." + IsadG.LANG_CODE),
        param="lang",
        render= (s: String) => Helpers.languageCodeToName(s)
      )
    )
  }

  def languageOfMaterial = Cached("pages:langMetric", metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      Query(
        defaultParams = Some(defaultParams.copy(entities = List(EntityType.DocumentaryUnit))),
        entityFacets = langCountFacets
      ).get[AnyModel].map(jsonResponse[AnyModel])
    }
  }


  private val holdingRepoFacets: FacetBuilder = { implicit request =>
    List(
      // Holding repository
      FieldFacetClass(
        key="repositoryName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder"
      )
    )
  }

  def holdingRepository = Cached("pages:repoMetric", metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      Query(
        defaultParams = Some(defaultParams.copy(entities = List(EntityType.DocumentaryUnit))),
        entityFacets = holdingRepoFacets
      ).get[AnyModel].map(jsonResponse[AnyModel])
    }
  }

  private val countryRepoFacets: FacetBuilder = { implicit request =>
    List(

      // Repositories by country
      FieldFacetClass(
        key="countryCode",
        name=Messages("repository.countryCode"),
        param="country",
        render=Helpers.countryCodeToName
      )
    )
  }

  def repositoryCountries = Cached("pages:repoCountryMetric", metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      Query(
       defaultParams = Some(defaultParams.copy(entities = List(EntityType.Repository))),
        entityFacets = countryRepoFacets
      ).get[AnyModel].map(jsonResponse[AnyModel])
    }
  }

  private val restrictedFacets: FacetBuilder = { implicit request =>
    List(
      // Historical agent type
      FieldFacetClass(
        key=solr.SolrConstants.RESTRICTED_FIELD,
        name=Messages("search.isRestricted"),
        param="restricted",
        render=s => Messages("restricted" + "." + s)
      )
    )
  }

  def restricted = Cached("pages:restrictedMetric", metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      Query(
        defaultParams = Some(defaultParams.copy(
          entities = List(EntityType.HistoricalAgent,
            EntityType.DocumentaryUnit, EntityType.HistoricalAgent))),
        entityFacets = restrictedFacets
      ).get[AnyModel].map(jsonResponse[AnyModel])
    }
  }


  private val agentTypeFacets: FacetBuilder = { implicit request =>
    List(
      // Historical agent type
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages("historicalAgent." + s)
      )
    )
  }

  def agentTypes = Cached("pages:agentTypeMetric", metricCacheTime) {
    userProfileAction.async { implicit userOpt => implicit request =>
      Query(
        defaultParams = Some(defaultParams.copy(entities = List(EntityType.HistoricalAgent))),
        entityFacets = agentTypeFacets
      ).get[AnyModel].map(jsonResponse[AnyModel])
    }
  }

  def clearCached = adminAction { implicit  userOpt => implicit request =>
    // Hack around lack of manual expiry
    Cache.remove("pages:agentTypeMetric")
    Cache.remove("pages:restrictedMetric")
    Cache.remove("pages:repoCountryMetric")
    Cache.remove("pages:repoMetric")
    Cache.remove("pages:langMetric")
    Redirect(globalConfig.routeRegistry.default)
  }
}
