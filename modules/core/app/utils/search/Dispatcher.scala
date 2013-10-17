package utils.search

import concurrent.Future
import rest.RestError
import models.UserProfile
import defines.EntityType

/**
 * User: mikebryant
 */
trait Dispatcher {
  def filter(params: SearchParams, filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[(String,String,EntityType.Value)]]]

  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList,
             filters: Map[String,Any] = Map.empty, mode: SearchMode.Value = SearchMode.DefaultAll)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]]

  def facet(facet: String, sort: String, params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList, filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,FacetPage[Facet]]]
}
