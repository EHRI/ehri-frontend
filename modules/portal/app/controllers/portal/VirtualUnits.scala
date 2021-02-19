package controllers.portal

import javax.inject._
import services.cypher.CypherService
import controllers.AppComponents
import controllers.base.SearchVC
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.{EntityType, _}
import models.base.Model
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.IdGenerator
import utils.PageParams
import services.search._

import scala.concurrent.Future


@Singleton
case class VirtualUnits @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  idGenerator: IdGenerator,
  cypher: CypherService,
  fc: FacetConfig
) extends PortalController
  with Generic[VirtualUnit]
  with Search
  with SearchVC {

  private val vuRoutes = controllers.portal.routes.VirtualUnits

  def browseVirtualCollection(id: String, dlid: Option[String]): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    if (isAjax) Ok(views.html.virtualUnit.itemDetailsVc(
      request.item, request.annotations, request.links, request.watched, dlid))
    else Ok(views.html.virtualUnit.show(
      request.item, request.annotations, request.links, request.watched, dlid))
  }

  def searchVirtualCollection(id: String, params: SearchParams, paging: PageParams, inline: Boolean): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      filters <- vcSearchFilters(request.item)
      result <- find[Model](
        params = params,
        paging = paging,
        filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = fc.docSearchFacets
      )
    } yield {
      if (isAjax) {
        if (inline) Ok(views.html.common.search.inlineItemList(result, watched, path = Seq(request.item)))
          .withHeaders("more" -> result.page.hasMore.toString)
        else Ok(views.html.virtualUnit.childItemSearch(request.item, result,
          vuRoutes.searchVirtualCollection(id), request.watched))
      }
      else Ok(views.html.virtualUnit.search(request.item, result,
        vuRoutes.searchVirtualCollection(id), request.watched))
    }
  }

  def browseVirtualCollections(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    val filters = if (!hasActiveQuery(request)) Map(SearchConstants.TOP_LEVEL -> true)
    else Map.empty[String,Any]

    find[VirtualUnit](
      params = params,
      paging = paging,
      filters = filters,
      entities = List(EntityType.VirtualUnit),
      facetBuilder = fc.docSearchFacets
    ).map { result =>
      Ok(views.html.virtualUnit.list(result, vuRoutes.browseVirtualCollections(),
        request.watched))
    }
  }

  def browseVirtualUnit(pathStr: String, id: String, dlid: Option[String]): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq
    val pathF: Future[Seq[Model]] = userDataApi.fetch[Model](pathIds).map(_.collect{ case Some(m) => m})
    val itemF: Future[Model] = userDataApi.getAny[Model](id)
    val linksF: Future[Seq[Link]] = userDataApi.links[Link](id)
    val annsF: Future[Seq[Annotation]] = userDataApi.annotations[Annotation](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      item <- itemF
      links <- linksF
      annotations <- annsF
      path <- pathF
    } yield {
      if (isAjax) Ok(views.html.virtualUnit.itemDetailsVc(item, annotations, links, watched, dlid, path :+ item))
      else Ok(views.html.virtualUnit.show(item, annotations, links, watched, dlid, path))
    }
  }

  def searchVirtualUnit(pathStr: String, id: String, params: SearchParams, paging: PageParams, inline: Boolean): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq
    val pathF: Future[Seq[Model]] = userDataApi.fetch[Model](pathIds).map(_.collect{ case Some(m) => m})
    val itemF: Future[Model] = userDataApi.getAny[Model](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      item <- itemF
      path <- pathF
      filters <- vcSearchFilters(item)
      result <- find[Model](
        params = params,
        paging = paging,
        filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = fc.docSearchFacets
      )
    } yield {
      if (isAjax) {
        if (inline) Ok(views.html.common.search.inlineItemList(result, watched, path = path :+ item))
          .withHeaders("more" -> result.page.hasMore.toString)
        else Ok(views.html.virtualUnit.childItemSearch(item, result,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
      }
      else Ok(views.html.virtualUnit.search(item, result,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
    }
  }
}

