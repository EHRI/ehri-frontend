package controllers.portal

import models.Isaar
import play.api.i18n.Messages
import views.Helpers
import utils.search.{FacetSort, FacetDisplay}
import solr.SolrConstants
import controllers.generic.Search
import play.api.mvc.{RequestHeader, Controller}
import utils.DateFacetUtils
import DateFacetUtils._
import solr.facet.SolrQueryFacet
import solr.facet.FieldFacetClass
import solr.facet.QueryFacetClass
import models.base.Description


/**
 * Facet configuration for various kinds of searches.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait FacetConfig extends Search {

  this: Controller =>
  
/**
   * Return a date query facet if valid start/end params have been given.
   */
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
   * Return a date query facet with an optional SolrQueryFacet if valid start/end have been given
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
        key = Description.LANG_CODE,
        name = Messages("documentaryUnit." + Description.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = "type",
        name = Messages("portal.type"),
        param = "type",
        render = s => Messages("portal.type." + s),
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
    List(
      // dateQuery(request),
      FieldFacetClass(
        key = Description.LANG_CODE,
        name = Messages("historicalAgent." + Description.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
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
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("facet.lod"),
        param="lod",
        render=s => Messages("facet.lod." + s),
        facets=List(
          SolrQueryFacet(value = "low", solrValue = "[0 TO 250]", name = Some("low")),
          SolrQueryFacet(value = "medium", solrValue = "[251 TO 1000]", name = Some("medium")),
          SolrQueryFacet(value = "high", solrValue = "[1001 TO *]", name = Some("high"))
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      )
    )
  }

  protected val annotationFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="isPromotable",
        name=Messages("portal.promotion.isPromotable"),
        param="promotable",
        render=s => Messages("portal.promotion.isPromotable." + s),
        facets=List(
          SolrQueryFacet(value = "true", solrValue = "true")
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key = "promotionScore",
        name = Messages("portal.promotion.score"),
        param = "score",
        render = (s: String) => Messages("portal.promotion.score." + s),
        facets=List(
          SolrQueryFacet(value = "positive", solrValue = "[1 TO *]"),
          SolrQueryFacet(value = "neutral", solrValue = "0"),
          SolrQueryFacet(value = "negative", solrValue = "[* TO -1]")
        ),
        display = FacetDisplay.List,
        sort = FacetSort.Fixed
      )
    )
  }

  protected val countryFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("facet.itemsHeldOnline"),
        param="data",
        render=s => Messages("facet.itemsHeldOnline." + s),
        facets=List(
          SolrQueryFacet(value = "yes", solrValue = "[1 TO *]", name = Some("yes"))
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("facet.lod"),
        param="lod",
        render=s => Messages("facet.lod." + s),
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
        name=Messages("facet.itemsHeldOnline"),
        param="data",
        render=s => Messages("facet.itemsHeldOnline." + s),
        facets=List(
          SolrQueryFacet(value = "yes", solrValue = "[1 TO *]", name = Some("yes"))
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("facet.lod"),
        param="lod",
        render=s => Messages("facet.lod." + s),
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
    List(
      //dateQuery(request),

      FieldFacetClass(
        key = Description.LANG_CODE,
        name = Messages("documentaryUnit." + Description.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key="isParent",
        name=Messages("facet.parent"),
        param="parent",
        render=s => Messages("facet.parent." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("facet.lod"),
        param="lod",
        render=s => Messages("facet.lod." + s),
        facets=List(
          SolrQueryFacet(value = "low", solrValue = "[0 TO 500]", name = Some("low")),
          SolrQueryFacet(value = "medium", solrValue = "[501 TO 2000]", name = Some("medium")),
          SolrQueryFacet(value = "high", solrValue = "[2001 TO *]", name = Some("high"))
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key="creationProcess",
        name=Messages("facet.source"),
        param="source",
        render=s => Messages("facet.source." + s),
        sort = FacetSort.Name,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("facet.holder"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("facet.country"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="accessPoints",
        name=Messages("facet.kw"),
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
        key = Description.LANG_CODE,
        name = Messages("cvocConcept." + Description.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      QueryFacetClass(
        key="isTopLevel",
        name=Messages("facet.container"),
        param="top",
        render=s => Messages("facet.topLevel." + s),
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
