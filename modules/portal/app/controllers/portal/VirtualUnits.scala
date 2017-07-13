package controllers.portal

import javax.inject._

import services.IdGenerator
import services.rest.cypher.Cypher
import controllers.AppComponents
import controllers.base.SearchVC
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models._
import models.base.AnyModel
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.PageParams
import utils.search._

import scala.concurrent.Future


@Singleton
case class VirtualUnits @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  idGenerator: IdGenerator,
  cypher: Cypher,
  fc: FacetConfig
) extends PortalController
  with Generic[VirtualUnit]
  with Search
  with SearchVC {

  private val vuRoutes = controllers.portal.routes.VirtualUnits

  def browseVirtualCollection(id: String): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    if (isAjax) Ok(views.html.virtualUnit.itemDetailsVc(
      request.item, request.annotations, request.links, request.watched))
    else Ok(views.html.virtualUnit.show(
      request.item, request.annotations, request.links, request.watched))
  }

  def searchVirtualCollection(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    for {
      filters <- vcSearchFilters(request.item)
      result <- find[AnyModel](
        params = params,
        paging = paging,
        filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = fc.docSearchFacets
      )
    } yield {
      if (isAjax) Ok(views.html.virtualUnit.childItemSearch(request.item, result,
        vuRoutes.searchVirtualCollection(id), request.watched))
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

  def browseVirtualUnit(pathStr: String, id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq
    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => userDataApi.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = userDataApi.getAny[AnyModel](id)
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
      if (isAjax) Ok(views.html.virtualUnit.itemDetailsVc(item, annotations, links, watched, path))
      else Ok(views.html.virtualUnit.show(item, annotations, links, watched, path))
    }
  }

  def searchVirtualUnit(pathStr: String, id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.split(",").toSeq
    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => userDataApi.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = userDataApi.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = request.userOpt.map(_.id))
    for {
      watched <- watchedF
      item <- itemF
      path <- pathF
      filters <- vcSearchFilters(item)
      result <- find[AnyModel](
        params = params,
        paging = paging,
        filters = filters,
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = fc.docSearchFacets
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

