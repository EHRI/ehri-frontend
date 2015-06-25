package eu.ehri.project.search.solr

import javax.inject.Inject

import defines.EntityType
import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Configuration, Logger}
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
case class SolrSearchConfig(
  handler: ResponseHandler,
  params: SearchParams = SearchParams.empty,
  filters: Map[String, Any] = Map.empty,
  idFilters: Seq[String] = Seq.empty,
  facets: Seq[AppliedFacet] =  Seq.empty,
  facetClasses: Seq[FacetClass[Facet]] = Seq.empty,
  extraParams: Map[String, Any] = Map.empty,
  mode: SearchMode.Value = SearchMode.DefaultAll
)(implicit val conf: play.api.Configuration, ws: WSClient)
  extends SearchEngineConfig {

  val logger: Logger = Logger(this.getClass)

  val queryBuilder = new SolrQueryBuilder(WriterType.Json)(conf)
  
  // Dummy value to satisfy the RestDAO trait...
  val userProfile: Option[UserProfile] = None

  lazy val solrPath = utils.serviceBaseUrl("solr", conf)

  def solrSelectUrl = solrPath + "/select"

  /**
   * Get the Solr URL...
   */
  def fullSearchUrl(query: Map[String,Seq[String]]) = utils.http.joinPath(solrSelectUrl, query)

  def dispatch(query: Map[String,Seq[String]]): Future[WSResponse] = {
    ws.url(solrSelectUrl)
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

    logger.info(fullSearchUrl(queryRequest))
    dispatch(queryRequest).map { response =>
      val parser = handler.getResponseParser(response.body)
      val facetClassList: Seq[FacetClass[Facet]] = parser.extractFacetData(facets, facetClasses)
      val page = Page(params.offset, params.countOrDefault, parser.count, parser.items)
      SearchResult(page, params, facets, facetClassList, spellcheck = parser.spellcheckSuggestion)
    }
  }

  override def withIdFilters(ids: Seq[String]): SearchEngineConfig = copy(idFilters = idFilters ++ ids)

  override def withFacets(f: Seq[AppliedFacet]): SearchEngineConfig = copy(facets = facets ++ f)

  override def setMode(mode: SearchMode.Value): SearchEngineConfig = copy(mode = mode)

  override def withFilters(f: Map[String, Any]): SearchEngineConfig = copy(filters = filters ++ f)

  override def setParams(params: SearchParams): SearchEngineConfig = copy(params = params)

  override def withFacetClasses(fc: Seq[FacetClass[Facet]]): SearchEngineConfig = copy(facetClasses = facetClasses ++ fc)

  override def withExtraParams(extra: Map[String, Any]): SearchEngineConfig = copy(extraParams = extraParams ++ extra)

  override def withIdExcludes(ids: Seq[String]): SearchEngineConfig = copy(params = params.copy(excludes = Some(ids.toList)))

  override def withEntities(entities: Seq[EntityType.Value]): SearchEngineConfig = copy(params = params.copy(entities = entities.toList))

  override def setEntity(entities: EntityType.Value*): SearchEngineConfig = copy(params = params.copy(entities = entities.toList))

  override def setSort(sort: SearchOrder.Value): SearchEngineConfig = copy(params = params.copy(sort = Some(sort)))
}

case class SolrSearchEngine @Inject()(handler: ResponseHandler, conf: Configuration, ws: WSClient) extends SearchEngine {
  def config = new SolrSearchConfig(handler)(conf, ws)
}