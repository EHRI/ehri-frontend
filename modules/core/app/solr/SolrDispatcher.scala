package solr

import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import play.api.libs.ws.WS
import play.api.Logger
import defines.EntityType
import scala.concurrent.Future
import utils.search._
import utils.search.SearchHit
import com.github.seratch.scalikesolr.request.QueryRequest
import backend.rest.Constants
import solr.SolrConstants


/**
 * Class for fetching query results from a Solr instance.
 * Implements the plugin implementation so other search
 * engines/mocks can be substituted.
 */
case class SolrDispatcher(queryBuilder: QueryBuilder, responseParser: ResponseParser) extends backend.rest.RestDAO with Dispatcher {

  // Dummy value to satisfy the RestDAO trait...
  val userProfile: Option[UserProfile] = None

  /**
   * Get the Solr URL...
   */
  private def buildSearchUrl(query: QueryRequest) = {
    "%s/select%s".format(
      play.Play.application.configuration.getString("solr.path"),
      query.queryString()
    )
  }

  /**
   * Filter items on name only, returning minimal data.
   * @param params A set of search params
   * @param filters A set of generic filters
   * @param userOpt An optional current user
   * @return a tuple of id, name, and type
   */
  def filter(params: SearchParams, filters: Map[String, Any] = Map.empty, extra: Map[String, Any] = Map.empty)(
    implicit userOpt: Option[UserProfile]): Future[ItemPage[(String, String, EntityType.Value, Option[String])]] = {
    val limit = params.limit.getOrElse(100)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    val queryRequest = queryBuilder.simpleFilter(params, filters, extra)
    Logger.logger.debug(queryRequest.queryString())

    WS.url(buildSearchUrl(queryRequest)).get().map { response =>
      val parser = responseParser(checkError(response).body)
      val items = parser.items.map(i => (i.itemId, i.name, i.`type`, i.fields.get(SolrConstants.HOLDER_NAME)))
      ItemPage(items, offset, limit, parser.count, Nil)
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
  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass[Facet]],
             filters: Map[String, Any] = Map.empty, extra: Map[String, Any] = Map.empty,
             mode: SearchMode.Value = SearchMode.DefaultAll)(
              implicit userOpt: Option[UserProfile]): Future[ItemPage[SearchHit]] = {
    val limit = params.limit.getOrElse(Constants.DEFAULT_LIST_LIMIT)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    val queryRequest = queryBuilder.search(params, facets, allFacets, filters, extra, mode)(userOpt)
    val url = buildSearchUrl(queryRequest)
    Logger.logger.debug("SOLR: {}", url)
    WS.url(buildSearchUrl(queryRequest)).get().map { response =>
      val parser = responseParser(checkError(response).body)
      ItemPage(parser.items, offset, limit, parser.count,
        parser.extractFacetData(facets, allFacets), spellcheck = parser.spellcheckSuggestion)
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
  def facet(facet: String, sort: FacetQuerySort.Value = FacetQuerySort.Name,
            params: SearchParams,
            facets: List[AppliedFacet], allFacets: List[FacetClass[Facet]],
            filters: Map[String, Any] = Map.empty, extra: Map[String,Any] = Map.empty)(
             implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]] = {
    val limit = params.limit.getOrElse(Constants.DEFAULT_LIST_LIMIT)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = queryBuilder.search(params, facets, allFacets, filters)(userOpt)

    WS.url(buildSearchUrl(queryRequest)).get().map { response =>
      val facetClasses = responseParser(checkError(response).body).extractFacetData(facets, allFacets)

      val facetClass = facetClasses.find(_.param == facet).getOrElse(
        throw new Exception("Unknown facet: " + facet))
      val facetLabels = sort match {
        case FacetQuerySort.Name => facetClass.sortedByName.slice(offset, offset + limit)
        case _ => facetClass.sortedByCount.slice(offset, offset + limit)
      }
      FacetPage(facetClass, facetLabels, offset, limit, facetClass.count)
    }
  }
}
