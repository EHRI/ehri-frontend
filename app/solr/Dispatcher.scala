package solr

import solr.facet.{FacetClass, AppliedFacet}
import concurrent.Future
import rest.RestError
import play.api.Plugin
import models.sql.User
import models.UserProfile
import defines.EntityType

/**
 * User: mikebryant
 */
trait Dispatcher extends Plugin {
  def filter(q: String, entityType: EntityType.Value, page: Option[Int] = Some(1), limit: Option[Int] = Some(100))(implicit userOpt: Option[UserProfile]): Future[Either[RestError,Seq[(String,String)]]]
  def list(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]]
  def facet(facet: String, sort: String, params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(implicit userOpt: Option[UserProfile]): Future[Either[RestError,solr.FacetPage[solr.facet.Facet]]]
}
