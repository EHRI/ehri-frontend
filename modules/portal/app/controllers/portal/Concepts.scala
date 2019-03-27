package controllers.portal

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController, Related}
import models.Concept
import models.base.Model
import play.api.mvc.{Action, AnyContent, ControllerComponents, RequestHeader}
import services.cypher.CypherService
import services.search._
import utils.PageParams


@Singleton
case class Concepts @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService,
  fc: FacetConfig
)(implicit val mat: Materializer)
  extends PortalController
  with Generic[Concept]
  with Related[Concept]
  with Search {

  private def filterKey(implicit request: RequestHeader): String =
    if (!hasActiveQuery(request)) SearchConstants.PARENT_ID else SearchConstants.ANCESTOR_IDS

  private val portalConceptRoutes = controllers.portal.routes.Concepts

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[Concept](params, paging, facetBuilder = fc.conceptFacets).map { result =>
      Ok(views.html.concept.list(result,
        portalConceptRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String, dlid: Option[String], params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemRelatedAction(id).async { implicit request =>
    find[Model](params, paging, idFilters = Some(request.links), sort = SearchSort.Name).map { result =>
      if (isAjax) Ok(views.html.common.search.searchItemList(result, request.watched))
        .withHeaders("more" -> result.page.hasMore.toString)
      else Ok(views.html.concept.show(request.item, request.annotations, result,
        portalConceptRoutes.browse(id, dlid), request.watched, dlid, request.links.nonEmpty))
    }
  }

  def search(id: String, dlid: Option[String], params: SearchParams, paging: PageParams, inline: Boolean): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[Concept](params, paging, filters = Map(filterKey -> id), facetBuilder = fc.conceptFacets, sort = SearchSort.Name).map { result =>
      if (isAjax) {
        if (inline) Ok(views.html.common.search.inlineItemList(result, request.watched))
          .withHeaders("more" -> result.page.hasMore.toString)
        else Ok(views.html.concept.childItemSearch(request.item, result,
          portalConceptRoutes.search(id), request.watched))
      }
      else Ok(views.html.concept.search(request.item, result,
        portalConceptRoutes.search(id, dlid), request.watched, dlid))
    }
  }
}
