package controllers.portal

import auth.AccountManager
import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import views.html.p
import utils.search._
import defines.EntityType
import backend.{IdGenerator, Backend}
import com.google.inject._
import scala.concurrent.Future
import controllers.portal.base.{Generic, PortalController}


@Singleton
case class VirtualUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
    accounts: AccountManager, idGenerator: IdGenerator, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Generic[VirtualUnit]
  with Search
  with FacetConfig {

  private val vuRoutes = controllers.portal.routes.VirtualUnits

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

  private def buildFilter(v: VirtualUnit): Map[String,Any] = {
    // Nastiness. We want a Solr query that will allow searching
    // both the child virtual collections of a VU as well as the
    // physical documentary units it includes. Since there is no
    // connection from the DU to VUs it belongs to (and creating
    // one is not feasible) we need to do this badness:
    // - load the VU from the graph along with its included DUs
    // - query for anything that has the VUs parent ID *or* anything
    // with an itemId among its included DUs
    import SearchConstants._
    val pq = v.includedUnits.map(_.id)
    if (pq.isEmpty) Map(s"$PARENT_ID:${v.id}" -> Unit)
    else Map(s"$PARENT_ID:${v.id} OR $ITEM_ID:(${pq.mkString(" ")})" -> Unit)
  }


  def browseVirtualCollection(id: String) = GetItemAction(id).apply { implicit request =>
      if (isAjax) Ok(p.virtualUnit.itemDetailsVc(request.item, request.annotations, request.links, request.watched))
      else Ok(p.virtualUnit.show(request.item, request.annotations, request.links, request.watched))
  }

  def searchVirtualCollection(id: String) = GetItemAction(id).async { implicit request =>
      find[AnyModel](
        filters = buildFilter(request.item),
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = docSearchFacets
      ).map { result =>
        if (isAjax) Ok(p.virtualUnit.childItemSearch(request.item, result,
          vuRoutes.searchVirtualCollection(id), request.watched))
        else Ok(p.virtualUnit.search(request.item, result,
          vuRoutes.searchVirtualCollection(id), request.watched))
      }
  }

  def browseVirtualCollections = UserBrowseAction.async { implicit request =>
    val filters = if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[VirtualUnit](
      filters = filters,
      entities = List(EntityType.VirtualUnit),
      facetBuilder = docSearchFacets
    ).map { result =>
      Ok(p.virtualUnit.list(result, vuRoutes.browseVirtualCollections(),
        request.watched))
    }
  }

  def browseVirtualUnit(pathStr: String, id: String) = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq
    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => backend.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val linksF: Future[Seq[Link]] = backend.getLinksForItem[Link](id)
    val annsF: Future[Seq[Annotation]] = backend.getAnnotationsForItem[Annotation](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      item <- itemF
      links <- linksF
      annotations <- annsF
      path <- pathF
    } yield {
      if (isAjax) Ok(p.virtualUnit.itemDetailsVc(item, annotations, links, watched, path))
      else Ok(p.virtualUnit.show(item, annotations, links, watched, path))
    }
  }

  def searchVirtualUnit(pathStr: String, id: String) = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq

    def includedChildren(parent: AnyModel): Future[SearchResult[(AnyModel,SearchHit)]] = parent match {
      case d: DocumentaryUnit => find[AnyModel](
        filters = Map(SearchConstants.PARENT_ID -> d.id),
        entities = List(d.isA),
        facetBuilder = docSearchFacets)
      case d: VirtualUnit => d.includedUnits match {
        case _ => find[AnyModel](
          filters = buildFilter(d),
          entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
          facetBuilder = docSearchFacets)
      }
      case _ => Future.successful(SearchResult.empty)
    }

    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => backend.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      item <- itemF
      path <- pathF
      children <- includedChildren(item)
    } yield {
      if (isAjax)
        Ok(p.virtualUnit.childItemSearch(item, children,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
      else Ok(p.virtualUnit.search(item, children,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
    }
  }
}

