package controllers.portal

import javax.inject.Inject

import models.Isaar
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.RequestHeader
import utils.DateFacetUtils
import utils.DateFacetUtils._
import services.search._
import views.Helpers


/**
  * Facet configuration for various kinds of searches.
  */
case class FacetConfig @Inject()(dateFacetUtils: DateFacetUtils)(implicit val messagesApi: MessagesApi) extends I18nSupport {

  import services.search.SearchConstants._

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
  val globalSearchFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.List,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = TYPE,
        name = Messages(TYPE),
        param = TYPE,
        render = s => Messages(TYPE + "." + s),
        display = FacetDisplay.List
      )
    )
  }

  // i.e. Metrics to count number and type of repos, docs, authorities
  // for display on the landing page.
  val entityMetrics: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = TYPE,
        name = Messages("facet.type"),
        param = TYPE,
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = Isaar.ENTITY_TYPE,
        name = Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param = "cpf",
        render = s => Messages("historicalAgent." + s),
        display = FacetDisplay.List
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

  val historicalAgentFacets: FacetBuilder = { implicit request =>
    List(
      // dateQuery(request),
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("historicalAgent." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.List,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = Isaar.ENTITY_TYPE,
        name = Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param = "cpf",
        render = s => Messages("historicalAgent." + s),
        display = FacetDisplay.List
      )
    )
  }

  val annotationFacets: FacetBuilder = { implicit request =>
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

  val countryFacets: FacetBuilder = { implicit request =>
    List()
  }

  val repositorySearchFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key = CHILD_COUNT,
        name = Messages("facet.data"),
        param = "data",
        render = s => Messages("facet.data." + s),
        facets = List(
           QueryFacet(value = "yes", range = Val("1") to End)
        )
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("facet.country"),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        displayLimit = 100
      )
    )
  }

  // Don't include country when displaying repository facets withing
  // a country record
  def localRepoFacets: RequestHeader => Seq[FacetClass[Facet]] = repositorySearchFacets
    .andThen(_.filterNot(_.key == COUNTRY_CODE))

  val docSearchFacets: FacetBuilder = { implicit request =>
    List(
      //dateQuery(request),
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.List,
        sort = FacetSort.Name,
        limit = Some(50)
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("facet.country"),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        displayLimit = 10
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("facet.holder"),
        param = "holder",
        displayLimit = 10,
        limit = Some(500)
      )
    )
  }

  val relatedSearchFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.List,
        sort = FacetSort.Name,
        limit = Some(50)
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("facet.holder"),
        param = "holder",
        displayLimit = 10,
        limit = Some(500)
      )
    )
  }

  // The facets for documents within a repository or another document shouldn't
  // contain the holder or country (since they'll be implied)
  def localDocFacets: RequestHeader => Seq[FacetClass[Facet]] = docSearchFacets.andThen(_.filterNot { fc =>
    Seq(TYPE, CREATION_PROCESS).contains(fc.key)
  })

  val conceptFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("cvocConcept." + LANGUAGE_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.List,
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
        display = FacetDisplay.List
      )
    )
  }
}
