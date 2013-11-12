package mocks

import defines.EntityType
import models.UserProfile
import scala.concurrent.Future
import utils.search._

/**
 * User: michaelb
 *
 * TODO: Integrate better with fixtures.
 *
 */
case class MockSearchDispatcher() extends Dispatcher {

  /** Class to aid in debugging the last submitted request - gross...
    */
  case class ParamLog(params: SearchParams, facets: List[AppliedFacet],
    allFacets: FacetClassList, filters: Map[String,Any] = Map.empty)

  val paramBuffer = collection.mutable.ArrayBuffer.empty[ParamLog]

  def filter(params: SearchParams, filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[ItemPage[(String,String, EntityType.Value)]] = {
    val docs = List(("c1", "Collection 1", EntityType.DocumentaryUnit), ("c2", "Collection 2", EntityType.DocumentaryUnit))
    val repo = List(("r1", "Repository 1", EntityType.Repository), ("r2", "Repository 2", EntityType.Repository))
    val items = params.entities match {
      case List(EntityType.DocumentaryUnit) => docs
      case List(EntityType.Repository) => repo
      case List(et) => Seq()
      case Nil => docs ++ repo
    }
    Future.successful {
      ItemPage(items, offset = 0, limit = params.limit.getOrElse(100), total = items.size, facets = Nil)
    }
  }

  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList,
             filters: Map[String,Any] = Map.empty, mode: SearchMode.Value = SearchMode.DefaultAll)(
      implicit userOpt: Option[UserProfile]): Future[ItemPage[SearchDescription]] = {
    paramBuffer += ParamLog(params, facets, allFacets, filters)
    val items = params.entities.foldLeft(List[SearchDescription]()) { case (listOfItems, et) =>
      et match {
        case EntityType.DocumentaryUnit => listOfItems ++ List(
          SearchDescription(itemId = "c1", id = "cd1", name = "Collection 1", `type` = EntityType.DocumentaryUnit),
          SearchDescription(itemId = "c2", id = "cd2", name = "Collection 2", `type` = EntityType.DocumentaryUnit),
          SearchDescription(itemId = "c3", id = "cd3", name = "Collection 3", `type` = EntityType.DocumentaryUnit),
          SearchDescription(itemId = "c4", id = "cd4", name = "Collection 4", `type` = EntityType.DocumentaryUnit))
        case EntityType.Repository => listOfItems ++ List(
          SearchDescription(itemId = "r1", id = "rd1", name = "Repository 1", `type` = EntityType.Repository),
          SearchDescription(itemId = "r2", id = "rd2", name = "Repository 2", `type` = EntityType.Repository))
        case _ => listOfItems // TODO: Implement other types
      }
    }
    Future.successful{
      ItemPage(items, 0, 20, items.size, Nil)
    }
  }

  def facet(facet: String, sort: String, params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList, filters: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]] = {

    // UNIMPLEMENTED
    Future.failed(new NotImplementedError())
  }
}
