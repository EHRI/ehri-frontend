package controllers.portal

import auth.AccountManager
import backend.rest.cypher.CypherDAO
import play.api.Logger
import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.RequestHeader
import views.html.p
import utils.search._
import defines.EntityType
import backend.{IdGenerator, Backend}
import com.google.inject._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
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

  private def buildFilter(item: AnyModel): Map[String,Any] = {
    // Nastiness. We want a Solr query that will allow searching
    // both the child virtual collections of a VU as well as the
    // physical documentary units it includes. Since there is no
    // connection from the DU to VUs it belongs to (and creating
    // one is not feasible) we need to do this badness:
    // - load the VU from the graph along with its included DUs
    // - query for anything that has the VUs parent ID *or* anything
    // with an itemId among its included DUs
    import SearchConstants._
    item match {
      case v: VirtualUnit =>
        val pq = v.includedUnits.map(_.id)
        if (pq.isEmpty) Map(s"$PARENT_ID:${v.id}" -> Unit)
        else Map(s"$PARENT_ID:${v.id} OR $ITEM_ID:(${pq.mkString(" ")})" -> Unit)
      case d => Map(s"$PARENT_ID:${d.id}" -> Unit)
    }
  }

  private def childIds(id: String): Future[Seq[String]] = {
    import play.api.libs.json._
    val dao = new CypherDAO()

    val reader: Reads[Seq[String]] =
      (__ \ "data").read[Seq[Seq[Seq[String]]]]
        .map { r => r.flatten.flatten }

    dao.get[Seq[String]](
      """
        |START vc = node:entities(__ID__ = {vcid})
        |MATCH vc<-[?:isPartOf*]-child,
        |      ddoc<-[?:includesUnit]-vc,
        |      doc<-[?:includesUnit]-child
        |RETURN DISTINCT collect(DISTINCT child.__ID__) + collect(DISTINCT doc.__ID__) + collect(DISTINCT ddoc.__ID__)
      """.stripMargin, Map("vcid" -> play.api.libs.json.JsString(id)))(reader).map { seq =>
        Logger.debug(s"Elements: ${seq.length}, distinct: ${seq.distinct.length}")
        seq.distinct
    }
  }

  def browseVirtualCollection(id: String) = GetItemAction(id).apply { implicit request =>
      if (isAjax) Ok(p.virtualUnit.itemDetailsVc(request.item, request.annotations, request.links, request.watched))
      else Ok(p.virtualUnit.show(request.item, request.annotations, request.links, request.watched))
  }

  def filtersOrIds(item: AnyModel)(implicit request: RequestHeader): Future[Map[String,Any]] = {
    if (!hasActiveQuery(request)) immediate(buildFilter(item))
    else childIds(item.id).map { seq =>
      Map(s"${SearchConstants.ANCESTOR_IDS}:(${seq.mkString(" ")})" -> Unit)
    }
  }

  def searchVirtualCollection(id: String) = GetItemAction(id).async { implicit request =>
    for {
      filters <- filtersOrIds(request.item)
      result <- find[AnyModel](
        filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = docSearchFacets
      )
    } yield {
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
    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => backend.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      item <- itemF
      path <- pathF
      filters <- filtersOrIds(item)
      result <- find[AnyModel](
        filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = docSearchFacets
      )
    } yield {
      if (isAjax)
        Ok(p.virtualUnit.childItemSearch(item, result,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
      else Ok(p.virtualUnit.search(item, result,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
    }
  }
}

