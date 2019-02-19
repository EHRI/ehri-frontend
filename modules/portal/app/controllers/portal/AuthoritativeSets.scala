package controllers.portal

import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import javax.inject.{Inject, Singleton}
import models.{AuthoritativeSet, HistoricalAgent}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.cypher.CypherService
import services.search._
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class AuthoritativeSets @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService,
  fc: FacetConfig
) extends PortalController
  with Generic[AuthoritativeSet]
  with Search {

  private val portalAuthSetRoutes = controllers.portal.routes.AuthoritativeSets

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[AuthoritativeSet](params = params, paging = paging, extra = Map("fq" -> "promotionScore:[1 TO *]"), sort = SearchSort.Name).map { result =>
      Ok(views.html.authoritativeSet.list(result, portalAuthSetRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.authoritativeSet.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[HistoricalAgent](params, paging,
        filters = Map(SearchConstants.HOLDER_ID -> id)).map { result =>
      Ok(views.html.authoritativeSet.show(request.item, result, request.annotations,
        request.links, portalAuthSetRoutes.search(id), request.watched))
    }
  }

  def search(id: String, params: SearchParams, paging: PageParams, inline: Boolean): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[HistoricalAgent](params, paging,
        filters = Map(SearchConstants.HOLDER_ID -> id)).map { result =>
      if (isAjax) {
        if (inline) Ok(views.html.common.search.inlineItemList(result, request.watched))
            .withHeaders("more" -> result.page.hasMore.toString)
        else Ok(views.html.authoritativeSet.childItemSearch(request.item, result,
          portalAuthSetRoutes.search(id), request.watched))
      }
      else Ok(views.html.authoritativeSet.search(request.item, result,
        portalAuthSetRoutes.search(id), request.watched))
    }
  }
}
