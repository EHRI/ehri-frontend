package solr

import solr.facet.{FacetClass, AppliedFacet}
import concurrent.Future
import rest.RestError
import play.api.Plugin
import models.UserProfile
import defines.EntityType

/**
 * User: mikebryant
 */
trait Dispatcher extends Plugin {
  def filter(q: String, entityType: Option[EntityType.Value] = None, page: Option[Int] = Some(1), limit: Option[Int] = Some(100))(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[(String,String,EntityType.Value)]]]
  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]]
  def facet(facet: String, sort: String, params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,solr.FacetPage[solr.facet.Facet]]]
}
