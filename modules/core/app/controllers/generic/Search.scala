package controllers.generic

import play.api.data.Form
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import defines.EntityType
import utils.Page
import utils.search._
import play.api.Logger
import controllers.base.CoreActionBuilders
import scala.concurrent.Future
import backend.{ContentType, WithId, Readable}


/**
 * Helpers for using the search engine from controllers.
 */
trait Search extends CoreActionBuilders {

  def searchEngine: utils.search.SearchEngine
  def searchResolver: utils.search.SearchItemResolver

  /**
   * A function that generates a list of facet classes from an
   * incoming request header. The facet rendering can be changed
   * based on request variables such as the user's current language.
   */
  type FacetBuilder = RequestHeader => Seq[FacetClass[Facet]]

  /**
   * A default facet class builder.
   */
  protected val emptyFacets: FacetBuilder = { lang => List.empty[FacetClass[Facet]]}

  /**
   * Ascertain if the user is making a textual query on this
   * request, as opposed to facet filtering a full list.
   */
  protected def hasActiveQuery(request: RequestHeader): Boolean =
    request.getQueryString(SearchParams.QUERY).filter(_.nonEmpty).isDefined

  private def bindFacetsFromRequest(facetClasses: Seq[FacetClass[Facet]])(implicit request: RequestHeader): Seq[AppliedFacet] =
    facetClasses.flatMap { fc =>
      request.queryString.get(fc.param).map(_.filterNot(_.trim.isEmpty)).map { values =>
        AppliedFacet(fc.key, values.toList)
      }
    }

  /**
   * Search sort logic. By default, if there's a query, items come out
   * sorted by their score. Otherwise, they are sorted by name.
   */
  private type SortFunction = (SearchParams => RequestHeader) => Option[SearchOrder.Value]

  private def defaultSortFunction(sp: SearchParams, request: RequestHeader,
      fallback: SearchOrder.Value = SearchOrder.DateNewest): Option[SearchOrder.Value] = {
    sp.sort.orElse {
      Some {
        if (request.getQueryString(SearchParams.QUERY).exists(!_.trim.isEmpty))
          SearchOrder.Score
        else fallback
      }
    }
  }

  /**
   * Fetch a search engine with configuration derived from
   * an incoming request.
   *
   * @param defaultParams the default parameters
   * @param defaultOrder  the default ordering
   * @param facetBuilder  a facet extractor
   * @param userOpt the current (optional user)
   * @param request the current request
   * @return a configured search engine
   */
  def searchEngineFromRequest(
    defaultParams: SearchParams = SearchParams.empty,
    defaultOrder: SearchOrder.Value = SearchOrder.DateNewest,
    facetBuilder: FacetBuilder = emptyFacets
  )(implicit userOpt: Option[UserProfile], request: RequestHeader): SearchEngine = {
    val params = defaultParams
      .copy(sort = defaultSortFunction(defaultParams, request, fallback = defaultOrder))

    val bound: Form[SearchParams] = SearchParams.form.bindFromRequest(request.queryString)

    val sp = bound
      .value.getOrElse(SearchParams.empty)
      .setDefault(Some(params))

    val facetClasses = facetBuilder(request)
    val appliedFacets: Seq[AppliedFacet] = bindFacetsFromRequest(facetClasses)

    searchEngine
      .setParams(sp)
      .withFacets(appliedFacets)
      .withFacetClasses(facetClasses)
  }

  /**
   * Helper for searching a set of pre-fetched items and then
   * combining the resulting search hits with those items.
   *
   * This applies an ID filter to the search query from the
   * IDs of the given items.
   *
   * @param items a sequence of items
   * @param filters A map of key/value filter pairs
   * @param extra An arbitrary set of key/value parameters
   * @param defaultOrder The default ordering
   * @param defaultParams The default parameters
   * @param idFilters Additional ID filters
   * @param entities A list of entities to limit the search to
   * @param facetBuilder A function to create the set of facets
   *                     from the incoming request
   * @param mode The search mode, default all or default to none
   * @return A query result containing the page of search data,
   *         plus the resolved parameters and facets.
   */
  def findIn[MT <: WithId](
    items: Seq[MT],
    filters: Map[String, Any] = Map.empty,
    extra: Map[String, Any] = Map.empty,
    defaultParams: SearchParams = SearchParams.empty,
    defaultOrder: SearchOrder.Value = SearchOrder.DateNewest,
    idFilters: Seq[String] = Seq.empty,
    entities: Seq[EntityType.Value] = Nil,
    facetBuilder: FacetBuilder = emptyFacets,
    mode: SearchMode.Value = SearchMode.DefaultAll)(
      implicit request: RequestHeader, userOpt: Option[UserProfile]): Future[SearchResult[(MT,SearchHit)]] = {

    val dispatcher = searchEngineFromRequest(defaultParams, defaultOrder, facetBuilder)
      .withFilters(filters)
      .withEntities(if (entities.isEmpty) defaultParams.entities else entities)
      .withIdFilters(idFilters)
      .withIdFilters(items.map(_.id))
      .withExtraParams(extra)
      .setMode(mode)

    dispatcher.search().map { result =>
      val resolved = result.page.items.flatMap(hit => items.find(_.id == hit.itemId).map(m => m -> hit))
      result.withItems(resolved)
    }
  }

  /**
   * Dispatch a search to the search engine.
   *
   * @param filters A map of key/value filter pairs
   * @param extra An arbitrary set of key/value parameters
   * @param defaultParams The default parameters
   * @param idFilters Additional ID filters
   * @param entities A list of entities to limit the search to
   * @param facetBuilder A function to create the set of facets
   *                     from the incoming request
   * @param mode The search mode, default all or default to none
   * @return A query result containing the page of search data,
   *         plus the resolved parameters and facets.
   */
  def find[MT](
    filters: Map[String, Any] = Map.empty,
    extra: Map[String, Any] = Map.empty,
    defaultParams: SearchParams = SearchParams.empty,
    defaultOrder: SearchOrder.Value = SearchOrder.DateNewest,
    idFilters: Seq[String] = Seq.empty,
    entities: Seq[EntityType.Value] = Nil,
    facetBuilder: FacetBuilder = emptyFacets,
    mode: SearchMode.Value = SearchMode.DefaultAll,
    resolverOpt: Option[SearchItemResolver] = None)(
      implicit request: RequestHeader, userOpt: Option[UserProfile], rd: Readable[MT]): Future[SearchResult[(MT,SearchHit)]] = {

    val dispatcher = searchEngineFromRequest(defaultParams, defaultOrder, facetBuilder)
      .withFilters(filters)
      .withEntities(entities)
      .withIdFilters(idFilters)
      .withExtraParams(extra)
      .setMode(mode)

    for {
      res <- dispatcher.search()
      list <- resolverOpt.getOrElse(searchResolver).resolve(res.page.items)
    } yield {
      if (list.size != res.page.size) {
        Logger.logger.warn("Items returned by search were not found in database: {} -> {}",
          (res.page.items.map(_.id), list))
      }
      res.copy(page = res.page.copy(items = list.zip(res.page.items)))
    }
  }

  /**
   * Dispatch a search for items of a single content type to the search engine.
   *
   * @param filters A map of key/value filter pairs
   * @param extra An arbitrary set of key/value parameters
   * @param defaultParams The default parameters
   * @param idFilters Additional ID filters
   * @param facetBuilder A function to create the set of facets
   *                     from the incoming request
   * @param mode The search mode, default all or default to none
   * @return A query result containing the page of search data,
   *         plus the resolved parameters and facets.
   */
  def findType[MT](
    filters: Map[String, Any] = Map.empty,
    extra: Map[String, Any] = Map.empty,
    defaultParams: SearchParams = SearchParams.empty,
    defaultOrder: SearchOrder.Value = SearchOrder.DateNewest,
    idFilters: Seq[String] = Seq.empty,
    facetBuilder: FacetBuilder = emptyFacets,
    mode: SearchMode.Value = SearchMode.DefaultAll,
    resolverOpt: Option[SearchItemResolver] = None)(
      implicit request: RequestHeader, userOpt: Option[UserProfile], rd: ContentType[MT]): Future[SearchResult[(MT,SearchHit)]] = {

    find[MT](filters, extra, defaultParams, defaultOrder, idFilters, Seq(rd.entityType), facetBuilder, mode, resolverOpt)
  }

  def filter[A](filters: Map[String, Any] = Map.empty, defaultParams: Option[SearchParams] = None)(implicit userOpt: Option[UserProfile], request: Request[A]): Future[Page[FilterHit]] = {
    val params = defaultParams.map(p => p.copy(sort = defaultSortFunction(p, request)))
    // Override the entity type with the controller entity type
    val sp = SearchParams.form.bindFromRequest
      .value.getOrElse(SearchParams.empty)
      .setDefault(params)

    searchEngine.setParams(sp).withFilters(filters).filter().map(_.page)
  }
}