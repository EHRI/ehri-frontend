package controllers.admin

import controllers.base.EntitySearch
import models.{Isaar, IsadG}
import models.base.AnyModel
import play.api.Play.current
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
import play.api.cache.Cached


@Singleton
class Metrics @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends EntitySearch {

  val searchEntities = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent
  )

  // For all of the metrics we're just using facet counts,
  // so set the result limit to be zero.
  private val defaultParams = SearchParams(limit=Some(0))

  private val langCountFacets = List(
    FieldFacetClass(
      key=IsadG.LANG_CODE,
      name=Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
      param="lang",
      render=Helpers.languageCodeToName
    )
  )

  def languageOfMaterial = Cached("pages:langMetric") {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = langCountFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
    }
  }


  private val holdingRepoFacets = List(
    // Holding repository
    FieldFacetClass(
      key="repositoryName",
      name=Messages("documentaryUnit.heldBy"),
      param="holder"
    )
  )

  def holdingRepository = Cached("pages:repoMetric") {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = holdingRepoFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
    }
  }

  private val countryRepoFacets = List(

    // Repositories by country
    FieldFacetClass(
      key="countryCode",
      name=Messages("isdiah.countryCode"),
      param="country",
      render=Helpers.countryCodeToName
    )
  )

  def repositoryCountries = Cached("pages:repoCountryMetric") {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.Repository))),
      entityFacets = countryRepoFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
    }
  }

  private val restrictedFacets = List(
    // Historical agent type
    FieldFacetClass(
      key=solr.SolrConstants.RESTRICTED_FIELD,
      name=Messages("search.isRestricted"),
      param="restricted",
      render=s => Messages("restricted" + "." + s)
    )
  )

  def restricted = Cached("pages:restrictedMetric") {
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


  private val agentTypeFacets = List(
    // Historical agent type
    FieldFacetClass(
      key=models.Isaar.ENTITY_TYPE,
      name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
      param="cpf",
      render=s => Messages(Isaar.FIELD_PREFIX + "." + s)
    )
  )

  def agentTypes = Cached("pages:agentTypeMetric") {
    searchAction[AnyModel](
      defaultParams = Some(defaultParams.copy(entities = List(EntityType.HistoricalAgent))),
      entityFacets = agentTypeFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        render {
          case _ => UnsupportedMediaType
        }
    }
  }
}
