package controllers.portal

import controllers.generic.Search
import models.Isaar
import play.api.i18n.{MessagesApi, Messages}
import play.api.mvc.{Controller, RequestHeader}
import utils.DateFacetUtils._
import utils.search._
import views.Helpers


/**
 * Facet configuration for various kinds of searches.
 */
trait FacetConfig extends Search {

  this: Controller with play.api.i18n.I18nSupport =>

  import utils.search.SearchConstants._

  implicit def messagesApi: MessagesApi

  private val dateFacetUtils = utils.DateFacetUtils()(messagesApi)

  /**
   * Return a date query facet if valid start/end params have been given.
   */
  private def dateList(implicit request: RequestHeader): Option[QueryFacet] = {
    for {
      dateString <- dateQueryForm.bindFromRequest(request.queryString).value
      queryRange = dateFacetUtils.formatAsQuery(dateString)
    } yield
      QueryFacet(
        value = dateString,
        range = queryRange,
        name = dateFacetUtils.formatReadable(dateString)
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
      facets = dateList(request).toList
    )
  }

  // i.e. Everything
  protected val globalSearchFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = TYPE,
        name = Messages(TYPE),
        param = TYPE,
        render = s => Messages(TYPE + "." + s),
        display = FacetDisplay.Choice
      )
    )
  }

  // i.e. Metrics to count number and type of repos, docs, authorities
  // for display on the landing page.
  protected val entityMetrics: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = TYPE,
        name = Messages("facet.type"),
        param = TYPE,
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key = Isaar.ENTITY_TYPE,
        name = Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param = "cpf",
        render = s => Messages("historicalAgent." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("repository.countryCode"),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key = HOLDER_ID,
        name = Messages("documentaryUnit.heldBy"),
        param = "holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  protected val historicalAgentFacets: FacetBuilder = { implicit request =>
    List(
      // dateQuery(request),
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("historicalAgent." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = Isaar.ENTITY_TYPE,
        name = Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param = "cpf",
        render = s => Messages("historicalAgent." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("historicalAgent.authoritativeSet"),
        param = "set",
        render = s => s,
        display = FacetDisplay.Choice
      )
    )
  }

  protected val annotationFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key = "isPromotable",
        name = Messages("facet.promotable"),
        param = "promotable",
        render = s => Messages("promotion.isPromotable." + s),
        facets = List(
          QueryFacet(value = "true", range = Val("true"))
        ),
        display = FacetDisplay.Boolean
      ),
      QueryFacetClass(
        key = "promotionScore",
        name = Messages("facet.score"),
        param = "score",
        render = (s: String) => Messages("promotion.score." + s),
        facets = List(
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
        key = CHILD_COUNT,
        name = Messages("facet.itemsHeldOnline"),
        param = "data",
        render = s => Messages("facet.itemsHeldOnline." + s),
        facets = List(
          QueryFacet(value = "yes", range = Val("1") to End)
        ),
        display = FacetDisplay.Boolean
      )
    )
  }

  protected val repositorySearchFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key = CHILD_COUNT,
        name = Messages("facet.itemsHeldOnline"),
        param = "data",
        render = s => Messages("facet.itemsHeldOnline." + s),
        facets = List(
          QueryFacet(value = "yes", range = Val("1") to End)
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("repository." + COUNTRY_CODE),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        displayLimit = 100
      )
    )
  }

  // Don't include country when displaying repository facets withing
  // a country record
  protected def localRepoFacets: (RequestHeader) => Seq[FacetClass[Facet]] = repositorySearchFacets
    .andThen(_.filterNot(_.key == COUNTRY_CODE))

  protected val docSearchFacets: FacetBuilder = { implicit request =>
    List(
      //dateQuery(request),
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("repository." + COUNTRY_CODE),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        displayLimit = 10
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("documentaryUnit.heldBy"),
        param = "holder",
        displayLimit = 10,
        limit = Some(500)
      ),
      FieldFacetClass(
        key = IS_PARENT,
        name = Messages("facet.parent"),
        param = "parent",
        render = s => Messages("facet.parent." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      )
    )
  }

  // The facets for documents within a repository or another document shouldn't
  // contain the holder or country (since they'll be implied)
  protected def localDocFacets: (RequestHeader) => Seq[FacetClass[Facet]] = docSearchFacets.andThen(_.filterNot { fc =>
      Seq(TYPE, CREATION_PROCESS).contains(fc.key)
    })

  protected val conceptFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("cvocConcept." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      QueryFacetClass(
        key = TOP_LEVEL,
        name = Messages("facet.container"),
        param = "top",
        render = s => Messages("facet.topLevel." + s),
        facets = List(
          QueryFacet(value = "true", range = Val("true"))
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("cvocConcept.inVocabulary"),
        param = "vocab",
        render = s => s,
        display = FacetDisplay.Choice
      )
    )
  }
}
