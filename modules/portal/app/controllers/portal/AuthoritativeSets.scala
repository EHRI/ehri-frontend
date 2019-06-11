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

  import SearchConstants._

  private val portalAuthSetRoutes = controllers.portal.routes.AuthoritativeSets
  private val promotedFilter = Map("fq" -> "promotionScore:[1 TO *]")

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    // This is a bit confusing: if there's not query we just list the promoted vocabularies.
    // If there is a query we have to fetch the promoted sets and then run the search
    // of items *held by* those sets.
    params.query.map { q =>
      for {
        // FIXME: more efficient ways of fetching promoted sets here?
        parents <- findType[AuthoritativeSet](SearchParams.empty, PageParams.empty.withoutLimit, extra = promotedFilter)
        filter = Map("fq" -> s"$HOLDER_ID:(${parents.mapItems(_._1.id).page.mkString(" ")})")
        result <- findType[HistoricalAgent](params = params, paging = paging, extra = filter)
      } yield Ok(views.html.authoritativeSet.list(result, portalAuthSetRoutes.searchAll(), request.watched))
    }.getOrElse {
      findType[AuthoritativeSet](params = params, paging = paging, extra = promotedFilter, sort = SearchSort.Name).map { result =>
        Ok(views.html.authoritativeSet.list(result, portalAuthSetRoutes.searchAll(), request.watched))
      }
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.authoritativeSet.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[HistoricalAgent](params, paging, filters = Map(SearchConstants.HOLDER_ID -> id), sort = SearchSort.Name).map { result =>
      Ok(views.html.authoritativeSet.show(request.item, result, request.annotations,
        request.links, portalAuthSetRoutes.search(id), request.watched))
    }
  }

  def search(id: String, params: SearchParams, paging: PageParams, inline: Boolean): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[HistoricalAgent](params, paging, filters = Map(SearchConstants.HOLDER_ID -> id), sort = SearchSort.Name).map { result =>
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
