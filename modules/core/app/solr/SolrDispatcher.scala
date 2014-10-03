package solr

import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import play.api.libs.ws.{WSResponse, WS}
import play.api.{Play, Logger}
import scala.concurrent.Future
import utils.search._
import utils.search.SearchHit
import com.github.seratch.scalikesolr.request.QueryRequest
import play.api.http.{MimeTypes, HeaderNames}


/**
 * Class for fetching query results from a Solr instance.
 * Implements the plugin implementation so other search
 * engines/mocks can be substituted.
 */
case class SolrDispatcher(queryBuilder: QueryBuilder, responseParser: ResponseParser) extends backend.rest.RestDAO with Dispatcher {

  import play.api.Play.current

  // Dummy value to satisfy the RestDAO trait...
  val userProfile: Option[UserProfile] = None

  lazy val solrPath = Play.current.configuration.getString("solr.path")
    .getOrElse(sys.error("Missing configuration: solr.path"))

  def solrSelectUrl = solrPath + "/select"

  /**
   * Get the Solr URL...
   */
  def fullSearchUrl(query: QueryRequest) = solrSelectUrl + query.queryString

  def queryAsForm(query: QueryRequest) = query.queryString().substring(1)

  def dispatch(query: QueryRequest): Future[WSResponse] = {
    Logger.logger.debug("SOLR: {}", fullSearchUrl(query))
    WS.url(solrSelectUrl)
      .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.FORM)
      .post(queryAsForm(query))
  }

  /**
   * Filter items on name only, returning minimal data.
   * @param params A set of search params
   * @param filters A set of generic filters
   * @param userOpt An optional current user
   * @return a tuple of id, name, and type
   */
  def filter(params: SearchParams, filters: Map[String, Any] = Map.empty, extra: Map[String, Any] = Map.empty)(
    implicit userOpt: Option[UserProfile]): Future[ItemPage[FilterHit]] = {

    val queryRequest = queryBuilder.simpleFilter(params, filters, extra)
    dispatch(queryRequest).map { response =>
      val parser = responseParser(checkError(response).body)
      val items = parser.items.map(i => FilterHit(i.itemId, i.id, i.name, i.`type`, i.fields.get(SolrConstants.HOLDER_NAME), i.gid))
      ItemPage(items, params.page, params.count, parser.count, Nil)
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

    val queryRequest = queryBuilder.search(params, facets, allFacets, filters, extra, mode)
    dispatch(queryRequest).map { response =>
      val parser = responseParser(checkError(response).body)
      ItemPage(parser.items, params.page, params.count, parser.count,
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

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = queryBuilder.search(params, facets, allFacets, filters)
    dispatch(queryRequest).map { response =>
      val facetClasses = responseParser(checkError(response).body).extractFacetData(facets, allFacets)

      val facetClass = facetClasses.find(_.param == facet).getOrElse(
        throw new Exception("Unknown facet: " + facet))
      val facetLabels = sort match {
        case FacetQuerySort.Name => facetClass.sortedByName.slice(params.offset, params.offset + params.count)
        case _ => facetClass.sortedByCount.slice(params.offset, params.offset + params.count)
      }
      FacetPage(facetClass, facetLabels, params.page, params.count, facetClass.count)
    }
  }
}
