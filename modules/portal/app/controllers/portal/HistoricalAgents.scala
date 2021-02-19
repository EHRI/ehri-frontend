package controllers.portal

import akka.stream.Materializer
import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController, Related}

import javax.inject.{Inject, Singleton}
import models.{EntityType, HistoricalAgent, Model}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.cypher.CypherService
import services.search.{SearchParams, SearchSort}
import utils.PageParams


@Singleton
case class HistoricalAgents @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService,
  fc: FacetConfig
)(implicit val mat: Materializer)
  extends PortalController
  with Generic[HistoricalAgent]
  with Related[HistoricalAgent]
  with Search {

  private val portalAgentRoutes = controllers.portal.routes.HistoricalAgents

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[HistoricalAgent](params = params, paging = paging, facetBuilder = fc.historicalAgentFacets).map { result =>
      Ok(views.html.historicalAgent.list(result,
        portalAgentRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemRelatedAction(id).async { implicit request =>
    find[Model](params, paging, idFilters = Some(request.links), sort = SearchSort.Name, facetBuilder = fc.relatedSearchFacets).map { result =>
      Ok(views.html.historicalAgent.show(request.item, request.annotations, result,
        portalAgentRoutes.browse(id), request.watched, request.links.nonEmpty))
    }
  }

  def export(id: String, asFile: Boolean): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.HistoricalAgent, id, Seq("eac"), asFile)
  }
}
