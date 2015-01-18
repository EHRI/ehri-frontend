package controllers.portal

import models.Isaar
import play.api.i18n.Messages
import views.Helpers
import utils.search._
import controllers.generic.Search
import play.api.mvc.{RequestHeader, Controller}
import utils.DateFacetUtils
import DateFacetUtils._
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
  private def dateList(implicit request: RequestHeader): Option[QueryFacet] = {
    for {
      dateString <- dateQueryForm.bindFromRequest(request.queryString).value
      queryRange = formatAsQuery(dateString)
    } yield
    QueryFacet(
      value = dateString,
      range = queryRange,
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
        name = Messages("type"),
        param = "type",
        render = s => Messages("type." + s),
        display = FacetDisplay.Choice
      )
    )
  }

  // i.e. Metrics to count number and type of repos, docs, authorities
  // for display on the landing page.
  protected val entityMetrics: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = SearchConstants.TYPE,
        name = Messages("facet.type"),
        param = SearchConstants.TYPE,
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
          QueryFacet(value = "low", range = Start to Val("250")),
          QueryFacet(value = "medium", range = Val("251") to Val("1000")),
          QueryFacet(value = "high", range = Val("1001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.Choice
      )
    )
  }

  protected val annotationFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="isPromotable",
        name=Messages("facet.promotable"),
        param="promotable",
        render=s => Messages("promotion.isPromotable." + s),
        facets=List(
          QueryFacet(value = "true", range = Val("true"))
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key = "promotionScore",
        name = Messages("facet.score"),
        param = "score",
        render = (s: String) => Messages("promotion.score." + s),
        facets=List(
          QueryFacet(value = "positive", range = Val("1") to End),
          QueryFacet(value = "neutral", range = Val("0")),
          QueryFacet(value = "negative", range = Start to Val("-1"))
        ),
        display = FacetDisplay.Choice,
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
          QueryFacet(value = "yes", range = Val("1") to Start)
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("facet.lod"),
        param="lod",
        render=s => Messages("facet.lod." + s),
        facets=List(
          QueryFacet(value = "low", range = Start to Val("200")),
          QueryFacet(value = "medium", range = Val("201") to Val("5000")),
          QueryFacet(value = "high", range = Val("5001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.Choice
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
          QueryFacet(value = "yes", range = Val("1") to End)
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("facet.lod"),
        param="lod",
        render=s => Messages("facet.lod." + s),
        facets=List(
          QueryFacet(value = "low", range = Start to Val("500")),
          QueryFacet(value = "medium", range = Val("501") to Val("2000")),
          QueryFacet(value = "high", range = Val("2001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.Choice
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

  // The facets for documents within a repository or another document shouldn't
  // contain the holder or country (since they'll be implied)
  protected def localDocFacets: RequestHeader => List[FacetClass[Facet]] = {
    docSearchFacets.andThen(fcl => fcl.filterNot { fc =>
      Seq("holder", "country", "source").contains(fc.param)
    })
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
          QueryFacet(value = "low", range = Start to Val("500")),
          QueryFacet(value = "medium", range = Val("501") to Val("2000")),
          QueryFacet(value = "high", range = Val("2001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.Choice
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
          QueryFacet(value = "true", range = Val("true"))
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
