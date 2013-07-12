package solr

import solr.facet.{SolrFacetClass, AppliedFacet}
import concurrent.Future
import rest.RestError
import play.api.Plugin
import models.UserProfile
import defines.EntityType
import utils.search.{SearchParams, ItemPage}

/**
 * User: mikebryant
 */
trait Dispatcher extends Plugin {
  def filter(params: SearchParams, filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[(String,String,EntityType.Value)]]]
  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: List[SolrFacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]]
  def facet(facet: String, sort: String, params: SearchParams, facets: List[AppliedFacet], allFacets: List[SolrFacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,solr.FacetPage[solr.facet.SolrFacet]]]
}
