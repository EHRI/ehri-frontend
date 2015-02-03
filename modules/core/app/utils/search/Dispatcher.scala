package utils.search

import defines.EntityType
import models.UserProfile

import scala.concurrent.Future

/**
 * User: mikebryant
 */
trait Dispatcher {

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
  def setParams(params: SearchParams): Dispatcher

  /**
   * Add additional applied facets to this request.
   */
  def withFacets(facets: Seq[AppliedFacet]): Dispatcher

  /**
   * Add additional facet classes to this request.
   */
  def withFacetClasses(fc: Seq[FacetClass[Facet]]): Dispatcher

  /**
   * Add additional key/value filters to this request.
   */
  def withFilters(filters: Map[String,Any]): Dispatcher

  /**
   * Add additional ID filters to this request.
   */
  def withIdFilters(ids: Seq[String]): Dispatcher

  /**
   * Add additional IDs to exclude.
   */
  def withIdExcludes(ids: Seq[String]): Dispatcher

  /**
   * Add entity type constraints.
   */
  def withEntities(entities: Seq[EntityType.Value]): Dispatcher

  /**
   * Set the sort order.
   */
  def setSort(sort: SearchOrder.Value): Dispatcher

  /**
   * Add additional engine-specific key/value parameters
   * to this request.
   */
  def withExtraParams(extra: Map[String,Any]): Dispatcher

  /**
   * Set the mode for this request.
   */
  def setMode(mode: SearchMode.Value): Dispatcher


  def filter()(implicit userOpt: Option[UserProfile]): Future[ItemPage[FilterHit]]

  def search()(implicit userOpt: Option[UserProfile]): Future[ItemPage[SearchHit]]

  def facet(facet: String, sort: FacetQuerySort.Value)(implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]]
}
