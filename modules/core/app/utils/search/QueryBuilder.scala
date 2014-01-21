package utils.search

import models.UserProfile
import com.github.seratch.scalikesolr.request.QueryRequest

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait QueryBuilder {
  def simpleFilter(params: SearchParams, filters: Map[String,Any] = Map.empty, alphabetical: Boolean = false)(
    implicit userOpt: Option[UserProfile]): QueryRequest

  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList, filters: Map[String,Any] = Map.empty,
             mode: SearchMode.Value = SearchMode.DefaultAll)(
              implicit userOpt: Option[UserProfile]): QueryRequest
}
