package mocks

import solr.{SearchDescription, ItemPage, SearchParams, Dispatcher}
import defines.EntityType
import models.UserProfile
import scala.concurrent.Future
import rest.RestError
import solr.facet.{FacetClass, AppliedFacet}

/**
 * User: michaelb
 *
 * TODO: Integrate better with fixtures.
 *
 */
case class MockSearchDispatcher(app: play.api.Application) extends Dispatcher {

  def filter(q: String, entityType: EntityType.Value, page: Option[Int] = Some(1), limit: Option[Int] = Some(100))(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,Seq[(String,String)]]] = {
    Future.successful {
      Right {
        entityType match {
          case EntityType.DocumentaryUnit => Seq("c1" -> "Collection 1", "c2" -> "Collection 2")
          case EntityType.Repository => Seq("r1" -> "Repository 1", "r2" -> "Repository 2")
          case _ => Seq()
        }
      }
    }
  }

  def list(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]] = {
    val items = params.entities.foldLeft(List[solr.SearchDescription]()) { case (listOfItems, et) =>
      et match {
        case EntityType.DocumentaryUnit => listOfItems ++ List(solr.SearchDescription("c1", "cd1", "Collection 1"),
          solr.SearchDescription("c2", "cd2", "Collection 2"),
          solr.SearchDescription("c3", "cd3", "Collection 3"),
          solr.SearchDescription("c4", "cd4", "Collection 4"))
        case EntityType.Repository => listOfItems ++ List(solr.SearchDescription("r1", "rd1", "Repository 1"),
          solr.SearchDescription("r2", "rd2", "Repository 2"))
        case _ => listOfItems // TODO: Implement other types
      }
    }
    Future.successful{
      Right {
        ItemPage(items, 0, 20, items.size, Nil)
      }
    }
  }

  def facet(facet: String, sort: String, params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,solr.FacetPage[solr.facet.Facet]]] = {

    // UNIMPLEMENTED
    Future.failed(new NotImplementedError())
  }
}
