package utils.search

import concurrent.Future
import models.UserProfile
import defines.EntityType

/**
 * User: mikebryant
 */
trait Dispatcher {
  def filter(params: SearchParams, filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[ItemPage[FilterHit]]

  def search(params: SearchParams, facets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]],
             filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty,
             mode: SearchMode.Value = SearchMode.DefaultAll)(
      implicit userOpt: Option[UserProfile]): Future[ItemPage[SearchHit]]

  def facet(facet: String, sort: FacetQuerySort.Value, params: SearchParams, facets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]], filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]]
}
