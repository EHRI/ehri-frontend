package controllers.portal

import models.{Isaar, IsadG}
import play.api.i18n.Messages
import views.Helpers
import utils.search.{FacetSort, FacetDisplay}
import solr.SolrConstants
import controllers.generic.Search
import play.api.mvc.{RequestHeader, Controller}
import utils.DateFacetUtils
import solr.facet.SolrQueryFacet
import solr.facet.FieldFacetClass
import solr.facet.QueryFacetClass
import defines.EntityType
import DateFacetUtils._

/**
 * Facet configuration for various kinds of searches.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait FacetConfig extends Search {

  this: Controller =>
  

  private def dateList(implicit request: RequestHeader): Option[SolrQueryFacet] = {
    for {
      dateString <- dateQueryForm.bindFromRequest(request.queryString).value
      solrQuery = formatAsSolrQuery(dateString)
    } yield 
    SolrQueryFacet(
      value = dateString,
      solrValue = solrQuery,
      name = formatReadable(dateString)
    )
  }
  /**
   * Return a date query facet if valid start/end params have been given.
   */
  private def dateQuery(implicit request: RequestHeader): QueryFacetClass = {
    QueryFacetClass(
      key = "dateRange",
      name = Messages("documentaryUnit." + DATE_PARAM),
      param = DATE_PARAM,
      sort = FacetSort.Fixed,
      display = FacetDisplay.Date,
      facets= dateList(request).toList
    )
  }

  // i.e. Everything
  protected val globalSearchFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages("documentaryUnit." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = "type",
        name = Messages("search.type"),
        param = "type",
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      )
    )
  }

  // i.e. Metrics to count number and type of repos, docs, authorities
  // for display on the landing page.
  protected val entityMetrics: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = SolrConstants.TYPE,
        name = Messages("search.type"),
        param = SolrConstants.TYPE,
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key=Isaar.ENTITY_TYPE,
        name=Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages("historicalAgent." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("repository.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  protected val historicalAgentFacets: FacetBuilder = { implicit request =>
    List(dateQuery(request))  ++
    List(
      FieldFacetClass(
        key=Isaar.ENTITY_TYPE,
        name=Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages("historicalAgent." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("historicalAgent.authoritativeSet"),
        param="set",
        render=s => s,
        display = FacetDisplay.Choice
      )
    )
  }

  protected val countryFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("portal.facet.itemsHeldOnline"),
        param="data",
        render=s => Messages("portal.facet.itemsHeldOnline." + s),
        facets=List(
          SolrQueryFacet(value = "yes", solrValue = "[1 TO *]", name = Some("yes"))
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("portal.facet.lod"),
        param="lod",
        render=s => Messages("portal.facet.lod." + s),
        facets=List(
          SolrQueryFacet(value = "low", solrValue = "[0 TO 200]", name = Some("low")),
          SolrQueryFacet(value = "medium", solrValue = "[201 TO 5000]", name = Some("medium")),
          SolrQueryFacet(value = "high", solrValue = "[5001 TO *]", name = Some("high"))
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      )
    )
  }

  protected val repositorySearchFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("portal.facet.itemsHeldOnline"),
        param="docs",
        render=s => Messages("portal.facet.itemsHeldOnline." + s),
        facets=List(
          SolrQueryFacet(value = "yes", solrValue = "[1 TO *]", name = Some("yes"))
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("portal.facet.lod"),
        param="lod",
        render=s => Messages("portal.facet.lod." + s),
        facets=List(
          SolrQueryFacet(value = "low", solrValue = "[0 TO 500]", name = Some("low")),
            SolrQueryFacet(value = "medium", solrValue = "[501 TO 2000]", name = Some("medium")),
            SolrQueryFacet(value = "high", solrValue = "[2001 TO *]", name = Some("high"))
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("repository.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  protected val docSearchFacets: FacetBuilder = { implicit request =>
    List(dateQuery(request)) ++
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages("documentaryUnit." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      QueryFacetClass(
        key="childCount",
        name=Messages("portal.facet.container"),
        param="items",
        render=s => Messages("portal.facet.container." + s),
        facets=List(
          SolrQueryFacet(value = "true", solrValue = "[1 TO *]", name = Some("hasChildItems"))
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("portal.facet.lod"),
        param="lod",
        render=s => Messages("portal.facet.lod." + s),
        facets=List(
          SolrQueryFacet(value = "low", solrValue = "[0 TO 500]", name = Some("low")),
          SolrQueryFacet(value = "medium", solrValue = "[501 TO 2000]", name = Some("medium")),
          SolrQueryFacet(value = "high", solrValue = "[2001 TO *]", name = Some("high"))
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("portal.facet.holder"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("portal.facet.location"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="accessPoints",
        name=Messages("Keyword"),
        param="kw",
        render= s => s,
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }
  
  protected val conceptFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages("cvocConcept." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      QueryFacetClass(
        key="isTopLevel",
        name=Messages("portal.facet.container"),
        param="top",
        render=s => Messages("portal.facet.topLevel." + s),
        facets=List(
          SolrQueryFacet(value = "true", solrValue = "true")
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("cvocConcept.inVocabulary"),
        param="vocab",
        render=s => s,
        display = FacetDisplay.Choice
      )
    )
  }
}
