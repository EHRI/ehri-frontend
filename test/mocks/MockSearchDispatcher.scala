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

  def filter(q: String, entityType: Option[EntityType.Value] = None, page: Option[Int] = Some(1), limit: Option[Int] = Some(100))(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[(String,String, EntityType.Value)]]] = {
    val docs = List(("c1", "Collection 1", EntityType.DocumentaryUnit), ("c2", "Collection 2", EntityType.DocumentaryUnit))
    val repo = List(("r1", "Repository 1", EntityType.Repository), ("r2", "Repository 2", EntityType.Repository))
    val items = entityType match {
      case Some(EntityType.DocumentaryUnit) => docs
      case Some(EntityType.Repository) => repo
      case Some(et) => Seq()
      case None => docs ++ repo
    }
    Future.successful {
      Right {
        ItemPage(items, offset = 0, limit = limit.getOrElse(100), total = items.size, facets = Nil)
      }
    }
  }

  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: List[FacetClass], filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[Either[RestError,ItemPage[SearchDescription]]] = {
    val items = params.entities.foldLeft(List[solr.SearchDescription]()) { case (listOfItems, et) =>
      et match {
        case EntityType.DocumentaryUnit => listOfItems ++ List(
          solr.SearchDescription(itemId = "c1", id = "cd1", name = "Collection 1", `type` = EntityType.DocumentaryUnit),
          solr.SearchDescription(itemId = "c2", id = "cd2", name = "Collection 2", `type` = EntityType.DocumentaryUnit),
          solr.SearchDescription(itemId = "c3", id = "cd3", name = "Collection 3", `type` = EntityType.DocumentaryUnit),
          solr.SearchDescription(itemId = "c4", id = "cd4", name = "Collection 4", `type` = EntityType.DocumentaryUnit))
        case EntityType.Repository => listOfItems ++ List(
          solr.SearchDescription(itemId = "r1", id = "rd1", name = "Repository 1", `type` = EntityType.Repository),
          solr.SearchDescription(itemId = "r2", id = "rd2", name = "Repository 2", `type` = EntityType.Repository))
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
