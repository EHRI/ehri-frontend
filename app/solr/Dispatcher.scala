package solr

import solr.facet.{FacetClass, AppliedFacet}
import concurrent.Future
import rest.RestError
import play.api.Plugin
import models.sql.User
import models.UserProfile

/**
 * User: mikebryant
 */
trait Dispatcher extends Plugin {
  def list(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]]
  def facet(facet: String, sort: String, params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(implicit userOpt: Option[UserProfile]): Future[Either[RestError,solr.FacetPage[solr.facet.Facet]]]
}
