package utils.search

import models.UserProfile

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait QueryBuilder {
  def simpleFilter(params: SearchParams, filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty, alphabetical: Boolean = false)(
    implicit userOpt: Option[UserProfile]): Map[String,Seq[String]]

  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList, filters: Map[String,Any] = Map.empty,
             extra: Map[String,Any] = Map.empty, mode: SearchMode.Value = SearchMode.DefaultAll)(
              implicit userOpt: Option[UserProfile]): Map[String,Seq[String]]
}
