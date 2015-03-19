package eu.ehri.project.search.solr

import defines.EntityType
import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import play.api.libs.ws.{WSResponse, WS}
import play.api.{Play, Logger}
import utils.Page
import scala.concurrent.Future
import utils.search._
import utils.search.SearchHit
import play.api.http.{MimeTypes, HeaderNames}


/**
 * Class for fetching query results from a Solr instance.
 * Implements the plugin implementation so other search
 * engines/mocks can be substituted.
 */
case class SolrSearchEngine(
  queryBuilder: QueryBuilder,
  handler: ResponseHandler,
  params: SearchParams = SearchParams.empty,
  filters: Map[String, Any] = Map.empty,
  idFilters: Seq[String] = Seq.empty,
  facets: Seq[AppliedFacet] =  Seq.empty,
  facetClasses: Seq[FacetClass[Facet]] = Seq.empty,
  extraParams: Map[String, Any] = Map.empty,
  mode: SearchMode.Value = SearchMode.DefaultAll
)(implicit val app: play.api.Application)
  extends SearchEngine {

  // Dummy value to satisfy the RestDAO trait...
  val userProfile: Option[UserProfile] = None

  lazy val solrPath = Play.current.configuration.getString("solr.path")
    .getOrElse(sys.error("Missing configuration: solr.path"))

  def solrSelectUrl = solrPath + "/select"

  /**
   * Get the Solr URL...
   */
  def fullSearchUrl(query: Map[String,Seq[String]]) = utils.joinPath(solrSelectUrl, query)

  def dispatch(query: Map[String,Seq[String]]): Future[WSResponse] = {
    WS.url(solrSelectUrl)
      .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.FORM)
      .post(query)
  }

  /**
   * Filter items on name only, returning minimal data.
   * @param userOpt An optional current user
   * @return a tuple of id, name, and type
   */
  override def filter()(implicit userOpt: Option[UserProfile]): Future[SearchResult[FilterHit]] = {

    val queryRequest = queryBuilder
      .setParams(params)
      .withIdFilters(idFilters)
      .withFilters(filters)
      .withExtraParams(extraParams)
      .simpleFilterQuery()

    dispatch(queryRequest).map { response =>
      val parser = handler.getResponseParser(response.body)
      val items = parser.items.map(i => FilterHit(
        i.itemId,
        i.id,
        i.fields.getOrElse(SearchConstants.NAME_EXACT, i.itemId),
        i.`type`,
        i.fields.get(SearchConstants.HOLDER_NAME),
        i.gid
      ))
      val page = Page(params.offset, params.countOrDefault, parser.count, items)
      SearchResult(page, params, Nil)
    }
  }

  /**
   * Do a full search with the given parameters.
   * @param userOpt An optional current users
   * @return a set of SearchDescriptions for matching results.
   */
  override def search()(implicit userOpt: Option[UserProfile]): Future[SearchResult[SearchHit]] = {

    val queryRequest = queryBuilder
      .setParams(params)
      .withFacets(facets)
      .withFacetClasses(facetClasses)
      .withFilters(filters)
      .withIdFilters(idFilters)
      .withExtraParams(extraParams)
      .setMode(mode)
      .searchQuery()

    Logger.logger.debug("SOLR: {}", fullSearchUrl(queryRequest))
    dispatch(queryRequest).map { response =>
      val parser = handler.getResponseParser(response.body)
      val facetClassList: Seq[FacetClass[Facet]] = parser.extractFacetData(facets, facetClasses)
      val page = Page(params.offset, params.countOrDefault, parser.count, parser.items)
      SearchResult(page, params, facets, facetClassList, spellcheck = parser.spellcheckSuggestion)
    }
  }

  /**
   * Return only facet counts for the given query, in pages.
   * @param facet The facet to fetch the count for.
   * @param sort How to sort the result, by name or count
   * @param userOpt An optional user
   * @return paged Facets
   */
  override def facet(facet: String, sort: FacetQuerySort.Value = FacetQuerySort.Name)(
             implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]] = {

    // create a response returning 0 documents - we don't
    // actually care about the documents, so even this is
    // not strictly necessary... we also don't care about the
    // ordering.
    val queryRequest = queryBuilder
      .setParams(params)
      .withFacets(facets)
      .withFacetClasses(facetClasses)
      .withIdFilters(idFilters)
      .withFilters(filters)
      .searchQuery()

    dispatch(queryRequest).map { response =>
      val fClasses = handler.getResponseParser(response.body).extractFacetData(facets, facetClasses)

      val facetClass = fClasses.find(_.param == facet).getOrElse(
        throw new Exception("Unknown facet: " + facet))
      val facetLabels = sort match {
        case FacetQuerySort.Name => facetClass.sortedByName.slice(params.offset, params.offset + params.countOrDefault)
        case _ => facetClass.sortedByCount.slice(params.offset, params.offset + params.countOrDefault)
      }
      FacetPage(facetClass, facetLabels, params.offset, params.countOrDefault, facetClass.count)
    }
  }

  override def withIdFilters(ids: Seq[String]): SearchEngine = copy(idFilters = idFilters ++ ids)

  override def withFacets(f: Seq[AppliedFacet]): SearchEngine = copy(facets = facets ++ f)

  override def setMode(mode: SearchMode.Value): SearchEngine = copy(mode = mode)

  override def withFilters(f: Map[String, Any]): SearchEngine = copy(filters = filters ++ f)

  override def setParams(params: SearchParams): SearchEngine = copy(params = params)

  override def withFacetClasses(fc: Seq[FacetClass[Facet]]): SearchEngine = copy(facetClasses = facetClasses ++ fc)

  override def withExtraParams(extra: Map[String, Any]): SearchEngine = copy(extraParams = extraParams ++ extra)

  override def withIdExcludes(ids: Seq[String]): SearchEngine = copy(params = params.copy(excludes = Some(ids.toList)))

  override def withEntities(entities: Seq[EntityType.Value]): SearchEngine = copy(params = params.copy(entities = entities.toList))

  override def setEntity(entities: EntityType.Value*): SearchEngine = copy(params = params.copy(entities = entities.toList))

  override def setSort(sort: SearchOrder.Value): SearchEngine = copy(params = params.copy(sort = Some(sort)))
}
