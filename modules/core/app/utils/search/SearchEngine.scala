package utils.search

import defines.EntityType
import models.UserProfile

import scala.concurrent.Future


trait SearchEngineConfig {

  /**
   * The general search parameters
   */
  def params: SearchParams

  /**
   * The set of applied facet filters.
   */
  def facets: Seq[AppliedFacet]

  /**
   * The set of active facet classes.
   */
  def facetClasses: Seq[FacetClass[Facet]]

  /**
   * Additional key/value filters.
   */
  def filters: Map[String,Any]

  /**
   * Item ID filters to constrain the search
   * to a specific subset of items.
   */
  def idFilters: Seq[String]

  /**
   * Additional engine-specific parameters.
   */
  def extraParams: Map[String,Any]

  /**
   * The search mode, specifying whether
   * to return all items by default, or none.
   */
  def mode: SearchMode.Value

  /**
   * Set the general search parameters.
   */
  def setParams(params: SearchParams): SearchEngineConfig

  /**
   * Add additional applied facets to this request.
   */
  def withFacets(facets: Seq[AppliedFacet]): SearchEngineConfig

  /**
   * Add additional facet classes to this request.
   */
  def withFacetClasses(fc: Seq[FacetClass[Facet]]): SearchEngineConfig

  /**
   * Add additional key/value filters to this request.
   */
  def withFilters(filters: Map[String,Any]): SearchEngineConfig

  /**
   * Add additional ID filters to this request.
   */
  def withIdFilters(ids: Seq[String]): SearchEngineConfig

  /**
   * Add additional IDs to exclude.
   */
  def withIdExcludes(ids: Seq[String]): SearchEngineConfig

  /**
   * Add entity type constraints.
   */
  def withEntities(entities: Seq[EntityType.Value]): SearchEngineConfig

  /**
   * Set entity type(s), overriding anything already set.
   */
  def setEntity(entities: EntityType.Value*): SearchEngineConfig

  /**
   * Set the sort order.
   */
  def setSort(sort: SearchOrder.Value): SearchEngineConfig

  /**
   * Add additional engine-specific key/value parameters
   * to this request.
   */
  def withExtraParams(extra: Map[String,Any]): SearchEngineConfig

  /**
   * Set the mode for this request.
   */
  def setMode(mode: SearchMode.Value): SearchEngineConfig

  /**
    * Run a quick filter
    */
  def filter()(implicit userOpt: Option[UserProfile]): Future[SearchResult[FilterHit]]

  /**
    * Run a full search
    */
  def search()(implicit userOpt: Option[UserProfile]): Future[SearchResult[SearchHit]]

  /**
    * Check service status
    * @return a status message
    */
  def status(): Future[String]
}

trait SearchEngine {
  def config: SearchEngineConfig
}
