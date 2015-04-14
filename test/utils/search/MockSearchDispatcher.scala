package utils.search

import defines.EntityType
import utils.Page

import scala.concurrent.ExecutionContext.Implicits._
import models._
import scala.concurrent.Future
import backend.{BackendHandle, Backend, ApiUser}
import models.base.{DescribedMeta, Described, Description, AnyModel}
import play.api.i18n.Lang

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
) extends SearchEngine {

  private val allEntities = Seq(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent,
    EntityType.VirtualUnit,
    EntityType.UserProfile
  )

  private implicit def handle(implicit userOpt: Option[UserProfile]): BackendHandle =
    backend.withContext(ApiUser(userOpt.map(_.id)))

  private def modelToFilterHit(m: AnyModel): FilterHit =
    FilterHit(m.id, m.id, m.toStringLang(Lang.defaultLang), m.isA, None, -1L)

  private def modelToSearchHit(m: AnyModel): SearchHit = m match {
    case d: DescribedMeta[Description,Described[Description]] => descModelToHit(d)
    case _ => SearchHit(m.id, m.id, m.isA, -1L, Map(
      SearchConstants.NAME_EXACT -> m.toStringLang(Lang.defaultLang)
    ))
  }

  private def descModelToHit[T <: DescribedMeta[Description,Described[Description]]](m: T): SearchHit = SearchHit(
    itemId = m.id,
    id = m.descriptions.headOption.flatMap(_.id).getOrElse("???"),
    `type` = m.isA,
    gid = m.meta.value.get("gid").flatMap(_.asOpt[Long]).getOrElse(-1L),
    fields = Map(
      SearchConstants.NAME_EXACT -> m.toStringLang(Lang.defaultLang)
    )
  )

  private def lookupItems(entities: Seq[EntityType.Value])(implicit userOpt: Option[UserProfile]): Future[Seq[AnyModel]] = {
    val types = if (entities.nonEmpty) entities else allEntities
    val resources = types.map(et => AnyModel.resourceFor(et))
    // Get the full listing for each type and concat them together
    // once all futures have completed...
    Future.sequence(resources.map(r => handle.list(r))).map(_.flatten)
  }

  override def filter()(implicit userOpt: Option[UserProfile]): Future[SearchResult[FilterHit]] =
    lookupItems(params.entities).map { items =>
      SearchResult(Page(
        offset = params.offset,
        limit = params.countOrDefault,
        total = items.size,
        items = items.map(modelToFilterHit)
      ), params)
    }

  override def search()(implicit userOpt: Option[UserProfile]): Future[SearchResult[SearchHit]] = {
    paramBuffer += ParamLog(params, facets, facetClasses, filters)
    lookupItems(params.entities).map { items =>
      val hits = items.map(modelToSearchHit)
      val withIds = hits.filter(h => idFilters.isEmpty || idFilters.contains(h.itemId))
      SearchResult(Page(
        offset = params.offset,
        limit = params.countOrDefault,
        total = withIds.size,
        items = withIds
      ), params)
    }
  }

  override def withIdFilters(ids: Seq[String]): SearchEngine = copy(idFilters = idFilters ++ ids)

  override def withFacets(f: Seq[AppliedFacet]): SearchEngine = copy(facets = facets ++ f)

  override def setMode(mode: SearchMode.Value): SearchEngine = copy(mode = mode)

  override def withFilters(f: Map[String, Any]): SearchEngine = copy(filters = filters ++ f)

  override def setParams(params: SearchParams): SearchEngine = copy(params = params)

  override def withFacetClasses(fc: Seq[FacetClass[Facet]]): SearchEngine = copy(facetClasses = facetClasses ++ fc)

  override def withExtraParams(extra: Map[String, Any]): SearchEngine = copy(extraParams = extraParams ++ extra)

  override def withIdExcludes(ids: Seq[String]): SearchEngine = copy(params = params.copy(excludes = Some(ids.toList)))

  override def withEntities(entities: Seq[EntityType.Value]): SearchEngine = copy(params = params.copy(entities = entities.toList))

  override def setEntity(entities: EntityType.Value*): SearchEngine = copy(params = params.copy(entities = entities.toList))

  override def setSort(sort: SearchOrder.Value): SearchEngine = copy(params = params.copy(sort = Some(sort)))
}
