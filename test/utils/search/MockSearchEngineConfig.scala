package utils.search

import javax.inject.Inject

import defines.EntityType
import utils.{PageParams, Page}

import scala.concurrent.ExecutionContext.Implicits._
import models._
import scala.concurrent.Future
import backend.{BackendHandle, Backend, ApiUser}
import models.base.{DescribedMeta, Described, Description, AnyModel}


/**
 * This class mocks a search displatcher by simply returning
 * whatever's in the backend, wrapped as a search hit...
 */
case class MockSearchEngineConfig(
  backend: Backend,
  paramLog: SearchLogger,
  params: SearchParams = SearchParams.empty,
  filters: Map[String, Any] = Map.empty,
  idFilters: Seq[String] = Seq.empty,
  facets: Seq[AppliedFacet] =  Seq.empty,
  facetClasses: Seq[FacetClass[Facet]] = Seq.empty,
  extraParams: Map[String, Any] = Map.empty,
  mode: SearchMode.Value = SearchMode.DefaultAll
)(implicit val messagesApi: play.api.i18n.MessagesApi) extends SearchEngineConfig with play.api.i18n.I18nSupport {

  private val allEntities = Seq(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent,
    EntityType.VirtualUnit,
    EntityType.UserProfile,
    EntityType.Annotation
  )

  private implicit def handle(implicit userOpt: Option[UserProfile]): BackendHandle =
    backend.withContext(ApiUser(userOpt.map(_.id)))

  private def modelToFilterHit(m: AnyModel): FilterHit =
    FilterHit(m.id, m.id, m.toStringLang, m.isA, None, -1L)

  private def modelToSearchHit(m: AnyModel): SearchHit = m match {
    case d: DescribedMeta[Description,Described[Description]] => descModelToHit(d)
    case _ => SearchHit(m.id, m.id, m.isA, -1L, Map(
      SearchConstants.NAME_EXACT -> m.toStringLang
    ))
  }

  private def descModelToHit[T <: DescribedMeta[Description,Described[Description]]](m: T): SearchHit = SearchHit(
    itemId = m.id,
    id = m.descriptions.headOption.flatMap(_.id).getOrElse("???"),
    `type` = m.isA,
    gid = m.meta.value.get("gid").flatMap(_.asOpt[Long]).getOrElse(-1L),
    fields = Map(
      SearchConstants.NAME_EXACT -> m.toStringLang
    )
  )

  private def lookupItems(entities: Seq[EntityType.Value])(implicit userOpt: Option[UserProfile]): Future[Seq[AnyModel]] = {
    val types = if (entities.nonEmpty) entities else allEntities
    val resources = types.map(et => AnyModel.resourceFor(et))
    // Get the full listing for each type and concat them together
    // once all futures have completed...
    // FIXME: HACK! If we fire off X parallel queries to the newly instantiated
    // backend we hit a rare syncronisation condition where the vertex index has not yet
    // been created, and multiple threads try to fetch-and-create it simultaneously.
    // This can sometimes result in NullPointerExceptions in the backend. This wouldn't
    // happen in real life since we only create the index at database instantiation time,
    // but it's an (occasional) issue when a mock search query is the first thing to hit
    // the database after setup. To get round this we fetch the first resource list
    // on its own, and the remainder in parallel.
    // A better way to resolve this might be to find a nicer way of mocking search
    // queries.
    handle.list(resources.head, PageParams.empty).flatMap { d =>
      Future.sequence(resources.tail.map(r => handle.list(r, PageParams.empty))).map { r =>
        d.items ++ r.flatten
      }
    }
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
    paramLog.log(ParamLog(params, facets, facetClasses, filters))
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

  override def withIdFilters(ids: Seq[String]): SearchEngineConfig = copy(idFilters = idFilters ++ ids)

  override def withFacets(f: Seq[AppliedFacet]): SearchEngineConfig = copy(facets = facets ++ f)

  override def setMode(mode: SearchMode.Value): SearchEngineConfig = copy(mode = mode)

  override def withFilters(f: Map[String, Any]): SearchEngineConfig = copy(filters = filters ++ f)

  override def setParams(params: SearchParams): SearchEngineConfig = copy(params = params)

  override def withFacetClasses(fc: Seq[FacetClass[Facet]]): SearchEngineConfig = copy(facetClasses = facetClasses ++ fc)

  override def withExtraParams(extra: Map[String, Any]): SearchEngineConfig = copy(extraParams = extraParams ++ extra)

  override def withIdExcludes(ids: Seq[String]): SearchEngineConfig = copy(params = params.copy(excludes = Some(ids.toList)))

  override def withEntities(entities: Seq[EntityType.Value]): SearchEngineConfig = copy(params = params.copy(entities = entities.toList))

  override def setEntity(entities: EntityType.Value*): SearchEngineConfig = copy(params = params.copy(entities = entities.toList))

  override def setSort(sort: SearchOrder.Value): SearchEngineConfig = copy(params = params.copy(sort = Some(sort)))
}

case class MockSearchEngine @Inject()(backend: Backend, messagesApi: play.api.i18n.MessagesApi, log: SearchLogger) extends SearchEngine {
  def config = new MockSearchEngineConfig(backend, log)(messagesApi)
}
