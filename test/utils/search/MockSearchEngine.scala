package utils.search

import javax.inject.Inject

import defines.EntityType
import utils.{Page, PageParams}

import scala.concurrent.ExecutionContext.Implicits._
import models._

import scala.concurrent.Future
import backend.{ApiUser, DataApi, DataApiHandle}
import models.base.{AnyModel, Described, DescribedMeta, Description}
import play.api.libs.json.JsString
import play.api.i18n.MessagesProvider


/**
 * This class mocks a search displatcher by simply returning
 * whatever's in the dataApi, wrapped as a search hit...
 */
case class MockSearchEngine @Inject()(
  dataApi: DataApi,
  paramLog: SearchLogger
)(implicit val messagesApi: play.api.i18n.MessagesApi, messagesProvider: MessagesProvider)
  extends SearchEngine with play.api.i18n.I18nSupport {

  private val allEntities = Seq(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.HistoricalAgent,
    EntityType.VirtualUnit,
    EntityType.UserProfile,
    EntityType.Annotation
  )

  private implicit def handle(implicit userOpt: Option[UserProfile]): DataApiHandle =
    dataApi.withContext(ApiUser(userOpt.map(_.id)))

  private def modelToFilterHit(m: AnyModel): FilterHit =
    FilterHit(m.id, m.id, m.toStringLang(messagesProvider.messages), m.isA, None, -1L)

  private def modelToSearchHit(m: AnyModel): SearchHit = m match {
    case d: DescribedMeta[Description,Described[Description]] => descModelToHit(d)
    case _ => SearchHit(m.id, m.id, m.isA, -1L, Map(
      SearchConstants.NAME_EXACT -> JsString(m.toStringLang(messagesProvider.messages))
    ))
  }

  private def descModelToHit[T <: DescribedMeta[Description,Described[Description]]](m: T): SearchHit = SearchHit(
    itemId = m.id,
    id = m.descriptions.headOption.flatMap(_.id).getOrElse("???"),
    `type` = m.isA,
    gid = m.meta.value.get("gid").flatMap(_.asOpt[Long]).getOrElse(-1L),
    fields = Map(
      SearchConstants.NAME_EXACT -> JsString(m.toStringLang(messagesProvider.messages))
    )
  )

  private def lookupItems(entities: Seq[EntityType.Value], userOpt: Option[UserProfile]): Future[Seq[AnyModel]] = {
    val types = if (entities.nonEmpty) entities else allEntities
    val resources = types.map(et => AnyModel.resourceFor(et))
    // Get the full listing for each type and concat them together
    // once all futures have completed...
    // FIXME: HACK! If we fire off X parallel queries to the newly instantiated
    // dataApi we hit a rare syncronisation condition where the vertex index has not yet
    // been created, and multiple threads try to fetch-and-create it simultaneously.
    // This can sometimes result in NullPointerExceptions in the dataApi. This wouldn't
    // happen in real life since we only create the index at database instantiation time,
    // but it's an (occasional) issue when a mock search query is the first thing to hit
    // the database after setup. To get round this we fetch the first resource list
    // on its own, and the remainder in parallel.
    // A better way to resolve this might be to find a nicer way of mocking search
    // queries.
    val apiHandle = handle(userOpt)
    apiHandle.list(resources.head, PageParams.empty).flatMap { d =>
      Future.sequence(resources.tail.map(r => apiHandle.list(r, PageParams.empty))).map { r =>
        d.items ++ r.flatten
      }
    }
  }

  override def status(): Future[String] = Future.successful("ok")

  override def filter(query: SearchQuery): Future[SearchResult[FilterHit]] = {
    lookupItems(query.params.entities, query.user).map { items =>
      SearchResult(Page(
        offset = query.paging.offset,
        limit = query.paging.limit,
        total = items.size,
        items = items.map(modelToFilterHit)
      ), query.params)
    }
  }

  override def search(query: SearchQuery): Future[SearchResult[SearchHit]] = {
    paramLog.log(ParamLog(query.params, query.appliedFacets, query.facetClasses, query.filters))
    lookupItems(query.params.entities, query.user).map { items =>
      val hits = items.map(modelToSearchHit)
      val withIds = hits.filter(h => query.withinIds.isEmpty || query.withinIds.contains(h.itemId))
      SearchResult(Page(
        offset = query.paging.offset,
        limit = query.paging.limit,
        total = withIds.size,
        items = withIds
      ), query.params)
    }
  }
}
