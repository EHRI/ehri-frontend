package solr

import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import play.api.libs.ws.WS
import play.api.Logger
import defines.EntityType
import rest.RestError
import scala.concurrent.Future
import utils.search._
import utils.search.SearchDescription
import com.github.seratch.scalikesolr.request.QueryRequest


/**
 * Class for fetching query results from a Solr instance.
 * Implements the plugin implementation so other search
 * engines/mocks can be substituted.
 */
case class SolrDispatcher() extends rest.RestDAO with Dispatcher {

  // Dummy value to satisfy the RestDAO trait...
  val userProfile: Option[UserProfile] = None

  /**
   * Get the Solr URL...
   * @param query
   * @return
   */
  private def buildSearchUrl(query: QueryRequest) = {
    "%s/select%s".format(
      play.Play.application.configuration.getString("solr.path"),
      query.queryString
    )
  }

  /**
   * Filter items on name only, returning minimal data.
   * @param params
   * @param filters
   * @param userOpt
   * @return a tuple of id, name, and type
   */
  def filter(params: SearchParams, filters: Map[String,Any] = Map.empty)(
    implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[(String,String,EntityType.Value)]]] = {
    val limit = params.limit.getOrElse(100)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    val queryRequest = SolrQueryBuilder.simpleFilter(params, filters)
    Logger.logger.debug(queryRequest.queryString())

    WS.url(buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val parser = SolrQueryParser(r.body)
        val items = parser.items.map(i => (i.itemId, i.name, i.`type`))
        ItemPage(items, offset, limit, parser.count, Nil)
      }
    }
  }

  /**
   * Do a full search with the given parameters.
   * @param params
   * @param facets
   * @param allFacets
   * @param filters
   * @param userOpt
   * @return a set of SearchDescriptions for matching results.
   */
  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass[Facet]], filters: Map[String,Any] = Map.empty)(implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]] = {
    val limit = params.limit.getOrElse(20)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    val queryRequest = SolrQueryBuilder.search(params, facets, allFacets, filters)(userOpt)
    Logger.logger.debug(queryRequest.queryString())

    WS.url(buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>
        val parser = SolrQueryParser(r.body)
        ItemPage(parser.items, offset, limit, parser.count,
          parser.extractFacetData(facets, allFacets), spellcheck = parser.spellcheckSuggestion)
      }
    }
  }

  /**
   * Return only facet counts for the given query, in pages.
   * @param facet
   * @param sort
   * @param params
   * @param facets
   * @param allFacets
   * @param filters
   * @param userOpt
   * @return paged Facets
   */
  def facet(facet: String, sort: String = "name", params: SearchParams,
        facets: List[AppliedFacet], allFacets: List[FacetClass[Facet]],
        filters: Map[String,Any] = Map.empty)(
          implicit userOpt: Option[UserProfile]): Future[Either[RestError,FacetPage[Facet]]] = {
    val limit = params.limit.getOrElse(20)
    val offset = (Math.max(params.page.getOrElse(1), 1) - 1) * limit

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = SolrQueryBuilder.search(params, facets, allFacets, filters)(userOpt)

    WS.url(buildSearchUrl(queryRequest)).get.map { response =>
      checkError(response).right.map { r =>

        val facetClasses = SolrQueryParser(r.body).extractFacetData(facets, allFacets)

        val facetClass = facetClasses.find(_.param==facet).getOrElse(
            throw new Exception("Unknown facet: " + facet))
        val facetLabels = sort match {
          case "name" => facetClass.sortedByName.slice(offset, offset + limit)
          case _ => facetClass.sortedByCount.slice(offset, offset + limit)
        }
        FacetPage(facetClass, facetLabels, offset, limit, facetClass.count)
      }
    }
  }
}
