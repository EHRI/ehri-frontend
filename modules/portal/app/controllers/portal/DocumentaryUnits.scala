package controllers.portal

import javax.inject.{Inject, Singleton}
import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.{DocumentaryUnit, EntityType, Model}
import play.api.mvc.{Action, AnyContent, ControllerComponents, RequestHeader}
import services.cypher.CypherService
import services.search._
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class DocumentaryUnits @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService,
  fc: FacetConfig
) extends PortalController
  with Generic[DocumentaryUnit]
  with Search {

  private val portalDocRoutes = controllers.portal.routes.DocumentaryUnits

  private def filterKey(implicit request: RequestHeader): String =
    if (!hasActiveQuery(request)) SearchConstants.PARENT_ID else SearchConstants.ANCESTOR_IDS

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    val filters = if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true)
    else Map.empty[String, Any]

    find[Model](params, paging, filters = filters, facetBuilder = fc.docSearchFacets,
      entities = Seq(EntityType.DocumentaryUnit, EntityType.VirtualUnit)).map { result =>
      Ok(views.html.documentaryUnit.list(result, portalDocRoutes.searchAll(),
        request.watched))
    }
  }

  def browse(id: String, dlid: Option[String], params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.documentaryUnit.itemDetails(request.item, request.annotations, request.links, request.watched, dlid)))
    else findType[DocumentaryUnit](params, paging,
      filters = Map(filterKey -> request.item.id), facetBuilder = fc.localDocFacets,
      sort = SearchSort.Id).map { result =>
      Ok(views.html.documentaryUnit.show(request.item, result, request.annotations,
        request.links, portalDocRoutes.search(id), request.watched, dlid))
    }
  }

  def search(id: String, dlid: Option[String], params: SearchParams, paging: PageParams, inline: Boolean): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[DocumentaryUnit](params, paging,
      filters = Map(filterKey -> request.item.id), facetBuilder = fc.localDocFacets,
      sort = SearchSort.Id).map { result =>
      if (isAjax) {
        if (inline) Ok(views.html.common.search.inlineItemList(result, request.watched))
          .withHeaders("more" -> result.page.hasMore.toString)
        else Ok(views.html.documentaryUnit.childItemSearch(request.item, result,
          portalDocRoutes.search(id, dlid), request.watched))
      }
      else Ok(views.html.documentaryUnit.search(request.item, result,
        portalDocRoutes.search(id), request.watched, dlid))
    }
  }

  def render(id: String, format: Option[String], asFile: Boolean): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    renderItem(EntityType.DocumentaryUnit, id, format, Seq("ead", "ead3"), asFile)
  }
}
