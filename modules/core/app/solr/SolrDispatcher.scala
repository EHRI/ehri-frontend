package solr

import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import play.api.libs.ws.{WSResponse, WS}
import play.api.{Play, Logger}
import scala.concurrent.Future
import utils.search._
import utils.search.SearchHit
import play.api.http.{MimeTypes, HeaderNames}



/**
 * Class for fetching query results from a Solr instance.
 * Implements the plugin implementation so other search
 * engines/mocks can be substituted.
 */
case class SolrDispatcher(queryBuilder: QueryBuilder, handler: WSResponse => QueryResponse)(implicit val app: play.api.Application)
  extends backend.rest.RestDAO with Dispatcher {

  // Dummy value to satisfy the RestDAO trait...
  val userProfile: Option[UserProfile] = None

  lazy val solrPath = Play.current.configuration.getString("solr.path")
    .getOrElse(sys.error("Missing configuration: solr.path"))

  def solrSelectUrl = solrPath + "/select"

  /**
   * Get the Solr URL...
   */
  def fullSearchUrl(query: Map[String,Seq[String]]) = solrSelectUrl + "?" + joinQueryString(query)

  def dispatch(query: Map[String,Seq[String]]): Future[WSResponse] = {
    Logger.logger.debug("SOLR: {}", fullSearchUrl(query))
    WS.url(solrSelectUrl)
      .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.FORM)
      .post(query)
  }

  /**
   * Filter items on name only, returning minimal data.
   * @param params A set of search params
   * @param filters A set of generic filters
   * @param userOpt An optional current user
   * @return a tuple of id, name, and type
   */
  override def filter(params: SearchParams, filters: Map[String, Any] = Map.empty, extra: Map[String, Any] = Map.empty)(
    implicit userOpt: Option[UserProfile]): Future[ItemPage[FilterHit]] = {

    val queryRequest = queryBuilder
      .setParams(params)
      .withFilters(filters).withExtraParams(extra)
      .simpleFilter()

    dispatch(queryRequest).map { response =>
      val parser = handler(checkError(response))
      val items = parser.items.map(i => FilterHit(
        i.itemId,
        i.id,
        i.fields.getOrElse(SearchConstants.NAME_EXACT, i.itemId),
        i.`type`,
        i.fields.get(SearchConstants.HOLDER_NAME),
        i.gid
      ))
      ItemPage(items, params.offset, params.countOrDefault, parser.count, Nil)
    }
  }

  /**
   * Do a full search with the given parameters.
   * @param params A set of search params
   * @param facets The *applied* facets
   * @param allFacets All enabled facets
   * @param filters A set of generic filters
   * @param userOpt An optional current users
   * @return a set of SearchDescriptions for matching results.
   */
  override def search(params: SearchParams, facets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]],
             filters: Map[String, Any] = Map.empty, extra: Map[String, Any] = Map.empty,
             mode: SearchMode.Value = SearchMode.DefaultAll)(
              implicit userOpt: Option[UserProfile]): Future[ItemPage[SearchHit]] = {

    val queryRequest = queryBuilder
      .setParams(params)
      .withFacets(facets)
      .withFacetClasses(allFacets)
      .withFilters(filters)
      .withExtraParams(extra)
      .setMode(mode)
      .search()

    dispatch(queryRequest).map { response =>
      val parser = handler(checkError(response))
      val facetClassList: Seq[FacetClass[Facet]] = parser.extractFacetData(facets, allFacets)
      ItemPage(parser.items, params.offset, params.countOrDefault, parser.count,
        facetClassList, spellcheck = parser.spellcheckSuggestion)
    }
  }

  /**
   * Return only facet counts for the given query, in pages.
   * @param facet The facet to fetch the count for.
   * @param sort How to sort the result, by name or count
   * @param params A set of search params
   * @param facets The applied facets
   * @param allFacets All enabled facets
   * @param filters   A set of generic filters
   * @param userOpt An optional user
   * @return paged Facets
   */
  override def facet(facet: String, sort: FacetQuerySort.Value = FacetQuerySort.Name,
            params: SearchParams,
            facets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]],
            filters: Map[String, Any] = Map.empty, extra: Map[String,Any] = Map.empty)(
             implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]] = {

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = queryBuilder
      .setParams(params)
      .withFacets(facets)
      .withFacetClasses(allFacets)
      .withFilters(filters)
      .search()

    dispatch(queryRequest).map { response =>
      val facetClasses = handler(checkError(response)).extractFacetData(facets, allFacets)

      val facetClass = facetClasses.find(_.param == facet).getOrElse(
        throw new Exception("Unknown facet: " + facet))
      val facetLabels = sort match {
        case FacetQuerySort.Name => facetClass.sortedByName.slice(params.offset, params.offset + params.countOrDefault)
        case _ => facetClass.sortedByCount.slice(params.offset, params.offset + params.countOrDefault)
      }
      FacetPage(facetClass, facetLabels, params.offset, params.countOrDefault, facetClass.count)
    }
  }
}
