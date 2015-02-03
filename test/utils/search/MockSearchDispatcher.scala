package utils.search

import defines.EntityType

import scala.concurrent.ExecutionContext.Implicits._
import models._
import scala.concurrent.Future
import backend.Backend
import models.base.{DescribedMeta, Described, Description, AnyModel}
import play.api.i18n.Lang
import backend.ApiUser

/*
 * Class to aid in debugging the last submitted request - gross...
 */
case class ParamLog(params: SearchParams, facets: Seq[AppliedFacet],
                    allFacets: Seq[FacetClass[Facet]], filters: Map[String,Any] = Map.empty)

/**
 * This class mocks a search displatcher by simply returning
 * whatever's in the backend, wrapped as a search hit...
 */
case class MockSearchDispatcher(
  backend: Backend,
  paramBuffer: collection.mutable.ListBuffer[ParamLog],
  params: SearchParams = SearchParams.empty,
  filters: Map[String, Any] = Map.empty,
  idFilters: Seq[String] = Seq.empty,
  facets: Seq[AppliedFacet] =  Seq.empty,
  facetClasses: Seq[FacetClass[Facet]] = Seq.empty,
  extraParams: Map[String, Any] = Map.empty,
  mode: SearchMode.Value = SearchMode.DefaultAll
) extends Dispatcher {

  private implicit def apiUser(implicit userOpt: Option[UserProfile]): ApiUser = ApiUser(userOpt.map(_.id))


  override def filter()(implicit userOpt: Option[UserProfile]): Future[ItemPage[FilterHit]] = {

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
        oftype, offset = params.offset, limit = params.countOrDefault, total = oftype.size, facets = Nil)
  }

  override def search()(implicit userOpt: Option[UserProfile]): Future[ItemPage[SearchHit]] = {
    paramBuffer += ParamLog(params, facets, facetClasses, filters)

    def descModelToHit[T <: DescribedMeta[Description,Described[Description]]](m: T): SearchHit = SearchHit(
        itemId = m.id,
        id = m.descriptions.headOption.flatMap(_.id).getOrElse("???"),
        `type` = m.isA,
        gid = m.meta.value.get("gid").flatMap(_.asOpt[Long]).getOrElse(-1L),
      fields = Map(
        SearchConstants.NAME_EXACT -> m.toStringLang(Lang.defaultLang)
      )
    )

    for {
      docs <- backend.list[DocumentaryUnit]()
      repos <- backend.list[Repository]()
      agents <- backend.list[HistoricalAgent]()
      virtualUnits <- backend.list[VirtualUnit]()
      all = docs.map(descModelToHit) ++ repos.map(descModelToHit) ++ agents.map(descModelToHit) ++ virtualUnits.map(descModelToHit)
      oftype = all.filter(h => params.entities.contains(h.`type`))
    } yield ItemPage(
      oftype, offset = params.offset, limit = params.countOrDefault, total = oftype.size, facets = Nil)
  }

  override def facet(facet: String, sort: FacetQuerySort.Value)(implicit userOpt: Option[UserProfile]): Future[FacetPage[Facet]] = ???

  override def withIdFilters(ids: Seq[String]): Dispatcher = copy(idFilters = idFilters ++ ids)

  override def withFacets(f: Seq[AppliedFacet]): Dispatcher = copy(facets = facets ++ f)

  override def setMode(mode: SearchMode.Value): Dispatcher = copy(mode = mode)

  override def withFilters(f: Map[String, Any]): Dispatcher = copy(filters = filters ++ f)

  override def setParams(params: SearchParams): Dispatcher = copy(params = params)

  override def withFacetClasses(fc: Seq[FacetClass[Facet]]): Dispatcher = copy(facetClasses = facetClasses ++ fc)

  override def withExtraParams(extra: Map[String, Any]): Dispatcher = copy(extraParams = extraParams ++ extra)

  override def withIdExcludes(ids: Seq[String]): Dispatcher = copy(params = params.copy(excludes = Some(ids.toList)))

  override def withEntities(entities: Seq[EntityType.Value]): Dispatcher = copy(params = params.copy(entities = entities.toList))

  override def setSort(sort: SearchOrder.Value): Dispatcher = copy(params = params.copy(sort = Some(sort)))
}
