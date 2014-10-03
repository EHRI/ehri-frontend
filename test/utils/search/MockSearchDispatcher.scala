package utils.search

import scala.concurrent.ExecutionContext.Implicits._
import models._
import scala.concurrent.Future
import backend.Backend
import models.base.{DescribedMeta, Described, Description, AnyModel}
import play.api.i18n.Lang
import backend.ApiUser

/**
 * This class mocks a search displatcher by simply returning
 * whatever's in the backend, wrapped as a search hit...
 *
 * User: michaelb
 */
case class MockSearchDispatcher(backend: Backend) extends Dispatcher {

  val paramBuffer = collection.mutable.ArrayBuffer.empty[ParamLog]

  /*
   * Class to aid in debugging the last submitted request - gross...
   */
  case class ParamLog(params: SearchParams, facets: List[AppliedFacet],
    allFacets: FacetClassList, filters: Map[String,Any] = Map.empty)

  
  def filter(params: SearchParams, filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[ItemPage[FilterHit]] = {

    def modelToHit(m: AnyModel): FilterHit =
      FilterHit(m.id, m.id, m.toStringLang(Lang.defaultLang), m.isA, None, 0L)

    for {
      docs <- backend.list[DocumentaryUnit]()
      repos <- backend.list[Repository]()
      agents <- backend.list[HistoricalAgent]()
      virtualUnits <- backend.list[VirtualUnit]()
      all = docs.map(modelToHit) ++ repos.map(modelToHit) ++ agents.map(modelToHit) ++ virtualUnits.map(modelToHit)
      oftype = all.filter(h => params.entities.contains(h.`type`))
    } yield ItemPage(
        oftype, page = params.pageOrDefault, count = params.countOrDefault, total = oftype.size, facets = Nil)
  }

  def search(params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList,
             filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty,
             mode: SearchMode.Value = SearchMode.DefaultAll)(
      implicit userOpt: Option[UserProfile]): Future[ItemPage[SearchHit]] = {
    paramBuffer += ParamLog(params, facets, allFacets, filters)

    def descModelToHit[T <: DescribedMeta[Description,Described[Description]]](m: T): SearchHit = SearchHit(
        itemId = m.id,
        id = m.descriptions.headOption.flatMap(_.id).getOrElse("???"),
        name = m.toStringLang(Lang.defaultLang),
        `type` = m.isA,
        gid = m.meta.value.get("gid").flatMap(_.asOpt[Long]).getOrElse(-1L)
    )

    for {
      docs <- backend.list[DocumentaryUnit]()
      repos <- backend.list[Repository]()
      agents <- backend.list[HistoricalAgent]()
      virtualUnits <- backend.list[VirtualUnit]()
      all = docs.map(descModelToHit) ++ repos.map(descModelToHit) ++ agents.map(descModelToHit) ++ virtualUnits.map(descModelToHit)
      oftype = all.filter(h => params.entities.contains(h.`type`))
    } yield ItemPage(
      oftype, page = params.pageOrDefault, count = params.countOrDefault, total = oftype.size, facets = Nil)
  }

  def facet(facet: String, sort: FacetQuerySort.Value, params: SearchParams, facets: List[AppliedFacet], allFacets: FacetClassList, filters: Map[String,Any] = Map.empty, extra: Map[String,Any] = Map.empty)(
      implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]] = {

    // UNIMPLEMENTED
    Future.failed(new NotImplementedError())
  }

  private implicit def apiUser(implicit userOpt: Option[UserProfile]): ApiUser = {
    new ApiUser(userOpt.map(_.id))
  }
}
