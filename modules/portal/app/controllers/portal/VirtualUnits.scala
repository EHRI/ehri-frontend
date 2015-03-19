package controllers.portal

import auth.AccountManager
import backend.rest.cypher.CypherDAO
import controllers.base.SearchVC
import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsNumber, JsObject}
import play.api.mvc.RequestHeader
import utils.search._
import defines.EntityType
import backend.{Entity, IdGenerator, Backend}
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
  with SearchVC
  with FacetConfig {

  private val vuRoutes = controllers.portal.routes.VirtualUnits

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

  private def childCount(id: String): Future[Int] = {
    import play.api.libs.json._
    val dao = new CypherDAO()
    val reader: Reads[Int] =
      (__ \ "data").read[Seq[Seq[Int]]].map { r => r.flatten.headOption.getOrElse(0) }

    dao.get[Int](
      """
        |START vc = node:entities(__ID__ = {vcid})
        |MATCH vc<-[?:isPartOf]-child,
        |      idoc<-[?:includesUnit]-vc
        |RETURN COUNT(DISTINCT child) + COUNT(DISTINCT idoc)
      """.stripMargin, Map("vcid" -> play.api.libs.json.JsString(id)))(reader).map { count =>
      count
    }
  }

  private def updateCount(m: AnyModel, c: Int): AnyModel = m match {
    case vc: VirtualUnit => vc.copy(meta = vc.meta.deepMerge(JsObject(Seq(Entity.CHILD_COUNT -> JsNumber(c)))))
    case d: DocumentaryUnit => d.copy(meta = d.meta.deepMerge(JsObject(Seq(Entity.CHILD_COUNT -> JsNumber(c)))))
    case o => o
  }

  def browseVirtualCollection(id: String) = GetItemAction(id).async { implicit request =>
    for {
      count <- childCount(id)
    } yield {
      if (isAjax) Ok(views.html.virtualUnit.itemDetailsVc(updateCount(request.item, count), request.annotations, request.links, request.watched))
      else Ok(views.html.virtualUnit.show(updateCount(request.item, count), request.annotations, request.links, request.watched))
    }
  }

  def filtersOrIds(item: AnyModel)(implicit request: RequestHeader): Future[Map[String,Any]] = {
    import SearchConstants._
    if (!hasActiveQuery(request)) immediate(buildFilter(item))
    else descendantIds(item.id).map { seq =>
      if (seq.isEmpty) Map.empty
      else Map(s"$ITEM_ID:(${seq.mkString(" ")}) OR $ANCESTOR_IDS:(${seq.mkString(" ")})" -> Unit)
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
      if (isAjax) Ok(views.html.virtualUnit.childItemSearch(request.item, result,
        vuRoutes.searchVirtualCollection(id), request.watched))
      else Ok(views.html.virtualUnit.search(request.item, result,
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
      Ok(views.html.virtualUnit.list(result, vuRoutes.browseVirtualCollections(),
        request.watched))
    }
  }

  def browseVirtualUnit(pathStr: String, id: String) = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq
    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => backend.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val countF: Future[Int] = childCount(id)
    val linksF: Future[Seq[Link]] = backend.getLinksForItem[Link](id)
    val annsF: Future[Seq[Annotation]] = backend.getAnnotationsForItem[Annotation](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      item <- itemF
      count <- countF
      links <- linksF
      annotations <- annsF
      path <- pathF
    } yield {
      if (isAjax) Ok(views.html.virtualUnit.itemDetailsVc(updateCount(item, count), annotations, links, watched, path))
      else Ok(views.html.virtualUnit.show(item, annotations, links, watched, path))
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
        Ok(views.html.virtualUnit.childItemSearch(item, result,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
      else Ok(views.html.virtualUnit.search(item, result,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
    }
  }
}

