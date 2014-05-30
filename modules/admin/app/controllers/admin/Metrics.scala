package controllers.admin

import models.{AccountDAO, Isaar, IsadG}
import models.base.AnyModel
import controllers.generic.Search
import play.api.Play.current
import defines.EntityType
import play.api.i18n.Messages
import views.Helpers
import utils.search._
import solr.facet.FieldFacetClass

import com.google.inject._
import play.api.cache.{Cache, Cached}
import backend.Backend


@Singleton
case class Metrics @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Search {

  private val metricCacheTime = 60 * 60 // 1 hour

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

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

  def languageOfMaterial = Cached.status(_ => "pages:langMetric", OK, metricCacheTime) {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = langCountFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
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

  def holdingRepository = Cached.status(_ => "pages:repoMetric", OK, metricCacheTime) {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = holdingRepoFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
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

  def repositoryCountries = Cached.status(_ => "pages:repoCountryMetric", OK, metricCacheTime) {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.Repository))),
      entityFacets = countryRepoFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
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

  def restricted = Cached.status(_ => "pages:restrictedMetric", OK, metricCacheTime) {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(
        entities = List(EntityType.HistoricalAgent,
          EntityType.DocumentaryUnit, EntityType.HistoricalAgent))),
      entityFacets = restrictedFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
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

  def agentTypes = Cached.status(_ => "pages:agentTypeMetric", OK, metricCacheTime) {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.HistoricalAgent))),
      entityFacets = agentTypeFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
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
