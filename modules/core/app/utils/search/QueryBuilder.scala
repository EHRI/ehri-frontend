package utils.search

import models.UserProfile

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait QueryBuilder {

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
  def setParams(params: SearchParams): QueryBuilder

  /**
   * Add additional applied facets to this request.
   */
  def withFacets(facets: Seq[AppliedFacet]): QueryBuilder

  /**
   * Add additional facet classes to this request.
   */
  def withFacetClasses(fc: Seq[FacetClass[Facet]]): QueryBuilder

  /**
   * Add additional key/value filters to this request.
   */
  def withFilters(filters: Map[String,Any]): QueryBuilder

  /**
   * Add additional ID filters to this request.
   */
  def withIdFilter(ids: Seq[String]): QueryBuilder

  /**
   * Add additional engine-specific key/value parameters
   * to this request.
   */
  def withExtraParams(extra: Map[String,Any]): QueryBuilder

  /**
   * Set the mode for this request.
   */
  def setMode(mode: SearchMode.Value): QueryBuilder

  /**
   * Build a simple filter query.
   */
  def simpleFilter(alphabetical: Boolean = false)(implicit userOpt: Option[UserProfile]): Map[String,Seq[String]]

  /**
   * Build a full search query.
   */
  def search()(implicit userOpt: Option[UserProfile]): Map[String,Seq[String]]
}
