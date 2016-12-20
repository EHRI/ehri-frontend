package controllers.portal

import javax.inject.{Inject, Singleton}

import backend.rest.cypher.Cypher
import controllers.Components
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.HistoricalAgent
import play.api.mvc.{Action, AnyContent}


@Singleton
case class HistoricalAgents @Inject()(
  components: Components,
  cypher: Cypher
) extends PortalController
  with Generic[HistoricalAgent]
  with Search
  with FacetConfig {

  private val portalAgentRoutes = controllers.portal.routes.HistoricalAgents

  def searchAll: Action[AnyContent] = UserBrowseAction.async { implicit request =>
    findType[HistoricalAgent](facetBuilder = historicalAgentFacets).map { result =>
      Ok(views.html.historicalAgent.list(result,
        portalAgentRoutes.searchAll(), request.watched))
    }
  }

  def browse(id: String): Action[AnyContent] = GetItemAction(id).apply { implicit request =>
    Ok(views.html.historicalAgent.show(request.item, request.annotations, request.links, request.watched))
  }

  def export(id: String, asFile: Boolean): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.HistoricalAgent, id, Seq("eac"), asFile)
  }
}
